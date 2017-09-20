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
import org.apache.ignite.ci.analysis.FullSuiteRunContext;
import org.apache.ignite.ci.logs.LogsAnalyzer;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Builds;
import org.apache.ignite.ci.model.conf.Project;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;
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

        final String logsProp = props.getProperty(HelperConfig.LOGS, "logs");
        final File logsDirFileConfigured = new File(logsProp);
        final File logsDirFile = logsDirFileConfigured.isAbsolute() ? logsDirFileConfigured : new File(workDir, logsProp);

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

    private CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        final CompletableFuture<List<File>> clearFileF = unzip(fut);
        return clearFileF.thenApplyAsync(files -> {
            Preconditions.checkState(!files.isEmpty(), "ZIP file can't be empty");
            return files.get(0);
        }, executor);
    }

    public List<CompletableFuture<File>> standardProcessLogs(int... buildIds) {
        List<CompletableFuture<File>> futures = new ArrayList<>();
        for (int buildId : buildIds) {
            final FullBuildInfo results = getBuildResults(buildId);
            final FullSuiteRunContext ctx = new FullSuiteRunContext(results);
            if (results.problemOccurrences != null) {
                ProblemOccurrences problems = getProblems(results.problemOccurrences.href);
                ctx.setProblems(problems.getProblemsNonNull());
            }
            final boolean timeout = ctx.hasTimeoutProblem();
            if (timeout)
                System.err.println(ctx.suiteName() + " failed with timeout " + buildId);

            final CompletableFuture<File> zipFut = downloadBuildLogZip(buildId);
            final CompletableFuture<File> clearLogFut = unzipFirstFile(zipFut);
            final ThreadDumpCopyHandler search = new ThreadDumpCopyHandler();
            final LastTestLogCopyHandler lastTestCp = new LastTestLogCopyHandler();
            lastTestCp.setDumpLastTest(timeout);
            final LogsAnalyzer analyzer = new LogsAnalyzer(search, lastTestCp);
            final CompletableFuture<File> fut2 = clearLogFut.thenApplyAsync(analyzer);
            futures.add(fut2);
        }
        return futures;
    }


    public List<CompletableFuture<File>> standardProcessAllBuildHistory(String buildTypeId, String branch) {
        List<Build> failed = this.getFinishedBuildsIncludeFailed(buildTypeId, branch);
        List<CompletableFuture<File>> fileFutList = standardProcessLogs(
            failed.stream().mapToInt(Build::getIdAsInt).toArray());
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

    public List<Build> getBuildHistory(BuildType type, String branchName) {
        return getBuildHistory(type.getId(), branchName, true, null);

    }

    public List<Build> getBuildHistory(String buildTypeId,
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

    public FullBuildInfo getBuildResults(String href) {
        return getJaxbUsingHref(href, FullBuildInfo.class);
    }

    public ProblemOccurrences getProblems(String href) {
        return getJaxbUsingHref(href, ProblemOccurrences.class);
    }

    private <T> T getJaxbUsingHref(String href, Class<T> elem) {
        return sendGetXmlParseJaxb(host + (href.startsWith("/") ? href.substring(1) : href), elem);
    }

    public int[] getBuildNumbersFromHistory(BuildType bt, String branchNameForHist) {
        List<Build> history = getBuildHistory(bt, branchNameForHist);
        return history.stream().mapToInt(Build::getIdAsInt).toArray();
    }

    @Override public void close()  {

    }

    public List<Build> getFinishedBuildsIncludeFailed(String buildTypeId,
        String branch) {
        String name = URLEncoder.encode(branch);
        List<Build> finished = getBuildHistory(buildTypeId,
            name,
            false,
            "finished");

        List<Build> nonCancelled = finished.stream().filter(build -> !"UNKNOWN".equals(build.status)).collect(Collectors.toList());
        return nonCancelled;
    }

    public String serverId() {
        return tcName;
    }
}
