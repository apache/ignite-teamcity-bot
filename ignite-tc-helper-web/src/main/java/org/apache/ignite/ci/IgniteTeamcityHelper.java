package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.JAXBException;
import org.apache.ignite.ci.analysis.ISuiteResults;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.logs.LogsAnalyzer;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.hist.Builds;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.util.UrlUtil;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.ci.util.ZipUtil;
import org.apache.ignite.internal.util.typedef.T2;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 21.07.2017.
 *
 *
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public class IgniteTeamcityHelper implements ITeamcity {

    public static final String TEAMCITY_HELPER_HOME = "teamcity.helper.home";
    private Executor executor;
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

        this.executor =  MoreExecutors.directExecutor();
    }

    public CompletableFuture<File> downloadBuildLogZip(int buildId) {
        boolean archive = true;
        Supplier<File> supplier = () -> {
            String buildIdStr = Integer.toString(buildId);
            final File buildDirectory = ensureDirExist(new File(logsDir, "buildId" + buildIdStr));
            final File file = new File(buildDirectory,
                "build.log" + (archive ? ".zip" : ""));
            if (file.exists() && file.canRead() && file.length() > 0) {
                System.out.println("Nothing to do, file is cached locally: [" + file + "]");
                return file;
            }
            String url = host + "downloadBuildLog.html" + "?buildId=" + buildIdStr + "&archived=true";

            try {
                HttpUtil.sendGetCopyToFile(basicAuthTok, url, file);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return file;
        };

        return supplyAsync(supplier, executor);
    }

    @Override public CompletableFuture<LogCheckResult> getLogCheckResults(Integer buildId, SingleBuildRunCtx ctx) {
        final Stopwatch started = Stopwatch.createStarted();

        return processBuildLog(buildId, ctx)
            .thenApply(t -> {
                System.out.println(Thread.currentThread().getName()
                    + ": processBuildLog required: " + started.elapsed(TimeUnit.MILLISECONDS)
                    + "ms for " + ctx.suiteId());
                return t;
            })
            .thenApply(T2::get2);
    }

    private String xmlEscapeText(String t) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < t.length(); i++){
            char c = t.charAt(i);
            switch(c){
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '\"': sb.append("&quot;"); break;
                case '&': sb.append("&amp;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if(c>0x7e) {
                        sb.append("&#"+((int)c)+";");
                    }else
                        sb.append(c);
            }
        }
        return sb.toString();
    }

    public void triggerBuild(String buildTypeId, String branchName) {
        triggerBuild(buildTypeId, branchName, false);
    }

    public void triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild) {
        String triggeringOptions = cleanRebuild ? " <triggeringOptions cleanSources=\"true\" rebuildAllDependencies=\"true\"/>" : "";
        String parameter = "<build branchName=\"" + xmlEscapeText(branchName) + "\">\n" +
            "    <buildType id=\"" +
            buildTypeId + "\"/>\n" +
            "    <comment><text>Build triggered from [" + this.getClass().getSimpleName()
            + ",cleanRebuild=" + cleanRebuild + "]</text></comment>\n" +
            triggeringOptions +
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
        final Build results = getBuild(buildId);
        final MultBuildRunCtx ctx = loadTestsAndProblems(results);

        if (ctx.hasTimeoutProblem())
            System.err.println(ctx.suiteName() + " failed with timeout " + buildId);

        return processBuildLog(ctx.getBuildId(), ctx).thenApply(T2::get1);
    }

    public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        final CompletableFuture<List<File>> clearFileF = unzip(fut);
        return clearFileF.thenApplyAsync(files -> {
            Preconditions.checkState(!files.isEmpty(), "ZIP file can't be empty");
            return files.get(0);
        }, executor);
    }

    public List<CompletableFuture<File>> standardProcessAllBuildHistory(String buildTypeId, String branch) {
        List<BuildRef> allBuilds = getFinishedBuildsIncludeSnDepFailed(buildTypeId, branch);
        return standardProcessLogs(allBuilds.stream().mapToInt(BuildRef::getId).toArray());
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
        return supplyAsync(() -> getProjectSuitesSync(projectId), executor);
    }

    private List<BuildType> getProjectSuitesSync(String projectId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/projects/" + projectId, Project.class)
            .getBuildTypesNonNull();
    }

    private <T> T sendGetXmlParseJaxb(String url, Class<T> rootElem) {
        try {
            try (InputStream inputStream = HttpUtil.sendGetWithBasicAuth(basicAuthTok, url)) {
                final InputStreamReader reader = new InputStreamReader(inputStream);
                return XmlUtil.load(rootElem, reader);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (JAXBException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<BuildRef> getBuildHistory(@Nullable String buildTypeId,
        @Nullable String branchName,
        boolean dfltFilter,
        @Nullable String state) {
        String btFilter = isNullOrEmpty(buildTypeId) ? "" : ",buildType:" + buildTypeId + "";
        String stateFilter = isNullOrEmpty(state) ? "" : (",state:" + state);
        String brachFilter = isNullOrEmpty(branchName) ? "" :",branch:" + branchName;

        return sendGetXmlParseJaxb(host + "app/rest/latest/builds"
            + "?locator="
            + "defaultFilter:" + dfltFilter
            + btFilter
            + stateFilter
            + brachFilter
            + ",count:1000", Builds.class).getBuildsNonNull();
    }

    public BuildTypeFull getBuildType(String buildTypeId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/buildTypes/id:" +
            buildTypeId, BuildTypeFull.class);
    }

    public Build getBuild(String href) {
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

    public CompletableFuture<TestOccurrenceFull> getTestFull(String href) {
        return supplyAsync(() -> getJaxbUsingHref(href, TestOccurrenceFull.class), executor);
    }

    public Change getChange(String href) {
        return getJaxbUsingHref(href, Change.class);
    }

    public ChangesList getChangesList(String href) {
        return getJaxbUsingHref(href, ChangesList.class);
    }

    private <T> T getJaxbUsingHref(String href, Class<T> elem) {
        return sendGetXmlParseJaxb(host + (href.startsWith("/") ? href.substring(1) : href), elem);
    }

    @Override public void close() {
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuilds(String projectId,
        String branch) {
        List<BuildRef> finished = getBuildHistory(projectId,
            UrlUtil.escape(branch),
            true,
            null);

        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch) {
        return getBuildsInState(projectId, branch, BuildRef.STATE_FINISHED);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getRunningBuilds(@Nullable String branch) {
        return supplyAsync(() -> getBuildsInState(null, branch, BuildRef.STATE_RUNNING), executor);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable String branch) {
        return supplyAsync(() -> getBuildsInState(null, branch, BuildRef.STATE_QUEUED), executor);
    }

    private List<BuildRef> getBuildsInState(
        @Nullable final String projectId,
        @Nullable final String branch,
        @Nonnull final String state) {
        List<BuildRef> finished = getBuildHistory(projectId,
            UrlUtil.escape(branch),
            false,
            state);
        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }

    public String serverId() {
        return tcName;
    }

    private CompletableFuture<T2<File, LogCheckResult>> processBuildLogNoCache(int buildId, ISuiteResults ctx) {
        final CompletableFuture<File> zipFut = downloadBuildLogZip(buildId);
        final CompletableFuture<File> clearLogFut = unzipFirstFile(zipFut);
        final ThreadDumpCopyHandler threadDumpCp = new ThreadDumpCopyHandler();
        final LastTestLogCopyHandler lastTestCp = new LastTestLogCopyHandler();
        boolean dumpLastTest = ctx.hasTimeoutProblem() || ctx.hasJvmCrashProblem() || ctx.hasOomeProblem();
        lastTestCp.setDumpLastTest(dumpLastTest);
        final LogsAnalyzer analyzer = new LogsAnalyzer(threadDumpCp, lastTestCp);
        final CompletableFuture<File> fut2 = clearLogFut.thenApplyAsync(analyzer);

        return fut2.thenApplyAsync(file -> {
            LogCheckResult logCheckResult = new LogCheckResult();
            if (dumpLastTest) {
                logCheckResult.setLastStartedTest(lastTestCp.getLastTestName());
                logCheckResult.setThreadDumpFileIdx(threadDumpCp.getLastFileIdx());
            }
            return new T2<>(file, logCheckResult);
        });
    }

    private ConcurrentHashMap<Integer, CompletableFuture<T2<File, LogCheckResult>>>
        buildLogProcessingRunning = new ConcurrentHashMap<>();

    public CompletableFuture<T2<File, LogCheckResult>> processBuildLog(int buildId, ISuiteResults ctx) {
        CompletableFuture<T2<File, LogCheckResult>> future = buildLogProcessingRunning.computeIfAbsent(buildId,
            k -> processBuildLogNoCache(k, ctx)
        );

        return future.thenApply(res -> {
            buildLogProcessingRunning.remove(buildId, future);

            return res;
        });

    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

}
