package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.xml.bind.JAXBException;
import org.apache.ignite.ci.actions.DownloadBuildLog;
import org.apache.ignite.ci.logs.LogsAnalyzer;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Builds;
import org.apache.ignite.ci.model.conf.Project;
import org.apache.ignite.ci.model.result.FullBuildInfo;
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

    private final Executor executor;
    private final File logsDir;
    /** Normalized Host address, ends with '/'. */
    private final String host;
    private final String basicAuthTok;
    private final String configName; //main properties file name

    public IgniteTeamcityHelper() throws IOException {
        this(null);
    }

    public IgniteTeamcityHelper(String tcName) throws IOException {
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
        String url = host + "/app/rest/buildQueue";
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
            final CompletableFuture<File> zipFut = downloadBuildLogZip(buildId);
            final CompletableFuture<File> clearLogFut = unzipFirstFile(zipFut);
            final ThreadDumpCopyHandler search = new ThreadDumpCopyHandler();
            final LogsAnalyzer analyzer = new LogsAnalyzer(search, new LastTestLogCopyHandler());
            final CompletableFuture<File> fut2 = clearLogFut.thenApplyAsync(analyzer);
            futures.add(fut2);
        }
        return futures;
    }

    /**
     * @return Basic auth token.
     */
    public String basicAuthToken() {
        return basicAuthTok;
    }

    /**
     * @return Host.
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
        return sendGetXmlParseJaxb(host + (href.startsWith("/") ? href.substring(1) : href), FullBuildInfo.class);
    }

    public int[] getBuildNumbersFromHistory(BuildType bt, String branchNameForHist) {
        List<Build> history = getBuildHistory(bt, branchNameForHist);
        return history.stream().map(Build::getId).mapToInt(Integer::parseInt).toArray();
    }

    @Override public void close() throws Exception {

    }
}
