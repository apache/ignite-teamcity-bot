package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.xml.bind.JAXBException;
import org.apache.ignite.ci.actions.DownloadBuildLog;
import org.apache.ignite.ci.analysis.FullBuildRunContext;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.Builds;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.ci.util.ZipUtil;

import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 21.07.2017.
 *
 *
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public class IgniteTeamcityHelper implements ITeamcity {

    public static final String TEAMCITY_HELPER_HOME = "teamcity.helper.home";
    private final Executor executor;
    private final File logsDir;
    /** Normalized Host address, ends with '/'. */
    private final String host;
    private final String basicAuthTok;
    private final String configName; //main properties file name
    private final String tcName;

    public IgniteTeamcityHelper() throws IOException {
        this(null);
    }

    public IgniteTeamcityHelper(String tcName) {
        this.tcName = tcName;
        final File workDir = HelperConfig.resolveWorkDir();

        this.configName = HelperConfig.prepareConfigName(tcName);

        final Properties props = HelperConfig.loadAuthProperties(workDir, configName);
        final String hostConf = props.getProperty(HelperConfig.HOST, "http://ci.ignite.apache.org/");

        this.host = hostConf + (hostConf.endsWith("/") ? "" : "/");
        basicAuthTok = HelperConfig.prepareBasicHttpAuthToken(props, configName);

        final File logsDirFile = HelperConfig.resolveLogs(workDir, props);

        logsDir = ensureDirExist(logsDirFile);
        this.executor = MoreExecutors.directExecutor();
    }

    public CompletableFuture<File> downloadBuildLogZip(int buildId) {
        final Supplier<File> buildLog = new DownloadBuildLog(buildId,
            host, basicAuthTok, logsDir, true);
        return CompletableFuture.supplyAsync(buildLog, executor);
    }

    public void triggerBuild(String buildTypeId, String branchName) {
        String parameter = "<build  branchName=\"" + branchName +
            "\">\n" +
            "    <buildType id=\"" +
            buildTypeId + "\"/>\n" +
            "    <comment><text>Build triggered from [" + this.getClass().getSimpleName() + "]</text></comment>\n" +
            //some fake property to avoid merging build in queue
            "    <properties>\n" +
            "        <property name=\"build.query.ts\" value=\"" + System.currentTimeMillis() + "\"/>\n" +
            "    </properties>\n" +
            "</build>";
        String url = host + "app/rest/buildQueue";
        try {
            HttpUtil.sendPostAsString(basicAuthTok, url, parameter);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CompletableFuture<List<File>> unzip(CompletableFuture<File> zipFileFut) {
        return zipFileFut.thenApplyAsync(ZipUtil::unZipToSameFolder, executor);
    }

    public List<CompletableFuture<File>> standardProcessLogs(int... buildIds) {
        List<CompletableFuture<File>> futures = new ArrayList<>();
        for (int buildId : buildIds) {
            futures.add(standardProcessOfBuildLog(buildId));
        }
        return futures;
    }

    private CompletableFuture<File> standardProcessOfBuildLog(int buildId) {
        final Build results = getBuildResults(buildId);
        final FullBuildRunContext ctx = loadTestsAndProblems(results);

        if (ctx.hasTimeoutProblem())
            System.err.println(ctx.suiteName() + " failed with timeout " + buildId);

        return processBuildLog(ctx);
    }

    public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        final CompletableFuture<List<File>> clearFileF = unzip(fut);
        return clearFileF.thenApplyAsync(files -> {
            Preconditions.checkState(!files.isEmpty(), "ZIP file can't be empty");
            return files.get(0);
        }, executor);
    }

    public List<CompletableFuture<File>> standardProcessAllBuildHistory(String buildTypeId, String branch) {
        List<BuildRef> allBuilds = this.getFinishedBuildsIncludeSnDepFailed(buildTypeId, branch);
        List<CompletableFuture<File>> fileFutList = standardProcessLogs(
            allBuilds.stream().mapToInt(BuildRef::getId).toArray());
        return fileFutList;
    }

    /**
     * @return Basic auth token.
     */
    public String basicAuthToken() {
        return basicAuthTok;
    }

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host() {
        return host;
    }

    /** {@inheritDoc} */
    public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return CompletableFuture.supplyAsync(() -> getProjectSuitesSync(projectId), executor);
    }

    public List<BuildType> getProjectSuitesSync(String projectId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/projects/" + projectId, Project.class)
            .getBuildTypesNonNull();
    }

    private <T> T sendGetXmlParseJaxb(String url, Class<T> rootElem) {
        try {
            String response = HttpUtil.sendGetAsString(basicAuthTok, url);
            return XmlUtil.load(response, rootElem);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (JAXBException e) {
            throw Throwables.propagate(e);
        }
    }

    public List<BuildRef> getBuildHistory(String buildTypeId,
        String branchName,
        boolean dfltFilter,
        @Nullable String state) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/builds" +
            "?locator=" +
            "buildType:" + buildTypeId
            + ",defaultFilter:" + dfltFilter
            + (state == null ? "" : (",state:" + state))
            + ",branch:" + branchName, Builds.class)
            .getBuildsNonNull();
    }

    public BuildTypeFull getBuildType(String buildTypeId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/buildTypes/id:" +
            buildTypeId, BuildTypeFull.class);
    }

    public Build getBuildResults(String href) {
        return getJaxbUsingHref(href, Build.class);
    }

    public ProblemOccurrences getProblems(String href) {
        return getJaxbUsingHref(href, ProblemOccurrences.class);
    }

    public TestOccurrences getTests(String href) {
        return getJaxbUsingHref(href, TestOccurrences.class);
    }

    public Statistics getBuildStat(String href) {
        return getJaxbUsingHref(href, Statistics.class);
    }

    public TestOccurrenceFull getTestFull(String href) {
        return getJaxbUsingHref(href, TestOccurrenceFull.class);
    }

    private <T> T getJaxbUsingHref(String href, Class<T> elem) {
        return sendGetXmlParseJaxb(host + (href.startsWith("/") ? href.substring(1) : href), elem);
    }

    @Override public void close() {
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuilds(String projectId,
        String branch) {
        String name = URLEncoder.encode(branch);
        List<BuildRef> finished = getBuildHistory(projectId,
            name,
            true,
            null);

        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId,
        String branch) {
        String name = URLEncoder.encode(branch);
        List<BuildRef> finished = getBuildHistory(projectId,
            name,
            false,
            "finished");

        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }

    public String serverId() {
        return tcName;
    }
}
