/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import org.apache.ignite.ci.analysis.ISuiteResults;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.LogCheckTask;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.logs.BuildLogStreamChecker;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.agent.AgentsRef;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.hist.Builds;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.issues.IssuesUsagesList;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.tcmodel.user.Users;
import org.apache.ignite.ci.teamcity.ITeamcityHttpConnection;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.util.UrlUtil;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.ci.util.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.apache.ignite.ci.HelperConfig.ensureDirExist;
import static org.apache.ignite.ci.util.XmlUtil.xmlEscapeText;

/**
 * Class for access to Teamcity REST API without any caching.
 *
 * See more info about API
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 * https://developer.github.com/v3/
 */
public class IgniteTeamcityConnection implements ITeamcity {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IgniteTeamcityConnection.class);

    /** Executor. */
    private Executor executor;

    /** Logs directory. */
    private File logsDir;

    /** Normalized Host address, ends with '/'. */
    private String host;

    /** TeamCity authorization token. */
    private String basicAuthTok;

    /** Teamcity http connection. */
    @Inject private ITeamcityHttpConnection teamcityHttpConn;

    /**  JIRA authorization token. */
    private String jiraBasicAuthTok;

    /** URL for JIRA integration. */
    private String jiraApiUrl;

    private String configName; //main properties file name
    private String tcName;

    /** Build logger processing running. */
    private ConcurrentHashMap<Integer, CompletableFuture<LogCheckTask>> buildLogProcessingRunning = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    public void init(@Nullable String tcName) {
        this.tcName = tcName;
        final File workDir = HelperConfig.resolveWorkDir();

        this.configName = HelperConfig.prepareConfigName(tcName);

        final Properties props = HelperConfig.loadAuthProperties(workDir, configName);
        final String hostConf = props.getProperty(HelperConfig.HOST, "https://ci.ignite.apache.org/");

        this.host = hostConf.trim() + (hostConf.endsWith("/") ? "" : "/");

        try {
            if (!Strings.isNullOrEmpty(props.getProperty(HelperConfig.USERNAME))
                    && props.getProperty(HelperConfig.ENCODED_PASSWORD) != null)
                setAuthToken(HelperConfig.prepareBasicHttpAuthToken(props, configName));
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to set credentials", e);
        }

        setJiraToken(HelperConfig.prepareJiraHttpAuthToken(props));
        setJiraApiUrl(props.getProperty(HelperConfig.JIRA_API_URL));

        final File logsDirFile = HelperConfig.resolveLogs(workDir, props);

        logsDir = ensureDirExist(logsDirFile);

        this.executor =  MoreExecutors.directExecutor();
    }

    /** {@inheritDoc} */
    @Override public void setAuthToken(String tok) {
        basicAuthTok = tok;
    }

    /** {@inheritDoc} */
    @Override public boolean isTeamCityTokenAvailable() {
        return basicAuthTok != null;
    }

    /** {@inheritDoc} */
    @Override public void setJiraToken(String tok) {
        jiraBasicAuthTok = tok;
    }

    /** {@inheritDoc} */
    @Override public boolean isJiraTokenAvailable() {
        return jiraBasicAuthTok != null;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public String sendJiraComment(String ticket, String comment) throws IOException {
        if (isNullOrEmpty(jiraApiUrl))
            throw new IllegalStateException("JIRA API URL is not configured for this server.");

        String url = jiraApiUrl + "issue/" + ticket + "/comment";

        return HttpUtil.sendPostAsStringToJira(jiraBasicAuthTok, url, "{\"body\": \"" + comment + "\"}");
    }

    /** {@inheritDoc} */
    @Override public void setJiraApiUrl(String url) {
        jiraApiUrl = url;
    }

    /** {@inheritDoc} */
    @Override public String getJiraApiUrl() {
        return jiraApiUrl;
    }


    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<Agent> agents(boolean connected, boolean authorized) {
        String url = "app/rest/agents?locator=connected:" + connected + ",authorized:" + authorized;

        return getJaxbUsingHref(url, AgentsRef.class)
            .getAgent()
            .stream()
            .parallel()
            .map(v -> getJaxbUsingHref(v.getHref(), Agent.class))
            .collect(Collectors.toList());
    }

    @AutoProfiling
    public CompletableFuture<File> downloadBuildLogZip(int buildId) {
        boolean archive = true;
        Supplier<File> supplier = () -> {
            String buildIdStr = Integer.toString(buildId);
            final File buildDir = ensureDirExist(new File(logsDir, "buildId" + buildIdStr));
            final File file = new File(buildDir,
                "build.log" + (archive ? ".zip" : ""));
            if (file.exists() && file.canRead() && file.length() > 0) {
                logger.info("Nothing to do, file is cached locally: [" + file + "]");

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

    @AutoProfiling
    @Override public CompletableFuture<LogCheckResult> analyzeBuildLog(Integer buildId, SingleBuildRunCtx ctx) {
        final Stopwatch started = Stopwatch.createStarted();

        CompletableFuture<LogCheckTask> fut = buildLogProcessingRunning.computeIfAbsent(buildId,
            k -> checkBuildLogNoCache(k, ctx)
        );

        return fut
            .thenApply(task -> {
                buildLogProcessingRunning.remove(buildId, fut);

                return task;
            })
            .thenApply(task -> {
                logger.info(Thread.currentThread().getName()
                    + ": processBuildLog required: " + started.elapsed(TimeUnit.MILLISECONDS)
                    + "ms for " + ctx.suiteId());

                return task;
            })
            .thenApply(LogCheckTask::getResult);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Build triggerBuild(
        String buildTypeId,
        String branchName,
        boolean cleanRebuild,
        boolean queueAtTop
    ) {
        String triggeringOptions =
            " <triggeringOptions" +
                " cleanSources=\"" + cleanRebuild + "\"" +
                " rebuildAllDependencies=\"" + cleanRebuild + "\"" +
                " queueAtTop=\"" + queueAtTop + "\"" +
                "/>";

        String comments = " <comment><text>Build triggered from Ignite TC Bot" +
            " [cleanRebuild=" + cleanRebuild + ", top=" + queueAtTop + "]</text></comment>\n";

        String param = "<build branchName=\"" + xmlEscapeText(branchName) + "\">\n" +
            "    <buildType id=\"" +
            buildTypeId + "\"/>\n" +
            comments +
            triggeringOptions +
            //some fake property to avoid merging build in queue
            "    <properties>\n" +
            "        <property name=\"build.query.loginTs\" value=\"" + System.currentTimeMillis() + "\"/>\n" +
            // "        <property name=\"testSuite\" value=\"org.apache.ignite.spi.discovery.tcp.ipfinder.elb.TcpDiscoveryElbIpFinderSelfTest\"/>\n" +
            "    </properties>\n" +
            "</build>";

        String url = host + "app/rest/buildQueue";

        try {
            logger.info("Triggering build: buildTypeId={}, branchName={}, cleanRebuild={}, queueAtTop={}",
                buildTypeId, branchName, cleanRebuild, queueAtTop);

            try (StringReader reader = new StringReader(HttpUtil.sendPostAsString(basicAuthTok, url, param))) {
                return XmlUtil.load(Build.class, reader);
            }
            catch (JAXBException e) {
                throw ExceptionUtil.propagateException(e);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CompletableFuture<List<File>> unzip(CompletableFuture<File> zipFileFut) {
        return zipFileFut.thenApplyAsync(ZipUtil::unZipToSameFolder, executor);
    }

    @AutoProfiling
    public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        final CompletableFuture<List<File>> clearFileF = unzip(fut);
        return clearFileF.thenApplyAsync(files -> {
            Preconditions.checkState(!files.isEmpty(), "ZIP file can't be empty");
            return files.get(0);
        }, executor);
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
    @Override public String host() {
        return host;
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return supplyAsync(() -> getProjectSuitesSync(projectId), executor);
    }

    private List<BuildType> getProjectSuitesSync(String projectId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/projects/" + projectId, Project.class)
            .getBuildTypesNonNull();
    }

    private <T> T sendGetXmlParseJaxb(String url, Class<T> rootElem) {
        try {
            try (InputStream inputStream = teamcityHttpConn.sendGet(basicAuthTok, url)) {
                final InputStreamReader reader = new InputStreamReader(inputStream);

                return loadXml(rootElem, reader);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (JAXBException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }



    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected <T> T loadXml(Class<T> rootElem, InputStreamReader reader) throws JAXBException {
        return XmlUtil.load(rootElem, reader);
    }

    /**
     * @param buildTypeId Build type id.
     * @param branchName Branch name.
     * @param dfltFilter Default filter.
     * @param state State.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @param sinceBuildId Since build id. Value is ignored if dates filter is present.
     */
    private List<BuildRef> getBuildHistory(@Nullable String buildTypeId,
        @Nullable String branchName,
        boolean dfltFilter,
        @Nullable String state,
        @Nullable Date sinceDate,
        @Nullable Date untilDate,
        @Nullable Integer sinceBuildId)  {
        String btFilter = isNullOrEmpty(buildTypeId) ? "" : ",buildType:" + buildTypeId;
        String stateFilter = isNullOrEmpty(state) ? "" : (",state:" + state);
        String branchFilter = isNullOrEmpty(branchName) ? "" :",branch:" + branchName;
        String sinceDateFilter = sinceDate == null ? "" : ",sinceDate:" + getDateYyyyMmDdTHhMmSsZ(sinceDate);
        String untilDateFilter = untilDate == null ? "" : ",untilDate:" + getDateYyyyMmDdTHhMmSsZ(untilDate);
        String buildNoFilter = sinceBuildId != null && sinceDateFilter.isEmpty() && untilDateFilter.isEmpty()
            ? ",sinceBuild:(id:" + sinceBuildId + ")" : "";

        return sendGetXmlParseJaxb(host + "app/rest/latest/builds"
            + "?locator="
            + "defaultFilter:" + dfltFilter
            + btFilter
            + stateFilter
            + branchFilter
            + buildNoFilter
            + ",count:" + DEFAULT_BUILDS_COUNT
            + sinceDateFilter
            + untilDateFilter, Builds.class).getBuildsNonNull();
    }

    public String getDateYyyyMmDdTHhMmSsZ(Date date){
        return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")
            .format(date)
            .replace("+", "%2B");
    }

    @AutoProfiling
    public BuildTypeFull getBuildType(String buildTypeId) {
        return sendGetXmlParseJaxb(host + "app/rest/latest/buildTypes/id:" +
            buildTypeId, BuildTypeFull.class);
    }

    @Override
    @AutoProfiling
    public Build getBuild(String href) {
        return getJaxbUsingHref(href, Build.class);
    }

    @Override
    @AutoProfiling
    public ProblemOccurrences getProblems(Build build) {
        if (build.problemOccurrences != null) {
            ProblemOccurrences coll = getJaxbUsingHref(build.problemOccurrences.href, ProblemOccurrences.class);

            coll.getProblemsNonNull().forEach(p -> p.buildRef = build);

            return coll;
        }
        else
            return new ProblemOccurrences();
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public TestOccurrences getTests(String href, String normalizedBranch) {
        return getJaxbUsingHref(href, TestOccurrences.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Statistics getBuildStatistics(String href) {
        return getJaxbUsingHref(href, Statistics.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public CompletableFuture<TestOccurrenceFull> getTestFull(String href) {
        return supplyAsync(() -> getJaxbUsingHref(href, TestOccurrenceFull.class), executor);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Change getChange(String href) {
        return getJaxbUsingHref(href, Change.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public ChangesList getChangesList(String href) {
        return getJaxbUsingHref(href, ChangesList.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public IssuesUsagesList getIssuesUsagesList(String href) { return getJaxbUsingHref(href, IssuesUsagesList.class); }

    /**
     * @param href Href.
     * @param elem Element class.
     */
    private <T> T getJaxbUsingHref(String href, Class<T> elem) {
        return sendGetXmlParseJaxb(host + (href.startsWith("/") ? href.substring(1) : href), elem);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuilds(String projectId, String branch) {

        return getFinishedBuilds(projectId, branch, null, null, null);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuilds(String projectId,
                                            String branch,
                                            Date sinceDate,
                                            Date untilDate,
                                            @Nullable Integer sinceBuildId) {
        List<BuildRef> finished = getBuildHistory(projectId,
            UrlUtil.escape(branch),
            true,
            null,
            sinceDate,
            untilDate,
            sinceBuildId);

        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch) {
        return getBuildsInState(projectId, branch, BuildRef.STATE_FINISHED, null);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch, Integer sinceBuildId) {
        return getBuildsInState(projectId, branch, BuildRef.STATE_FINISHED, sinceBuildId);
    }

    /** {@inheritDoc} */
    @Override
    @AutoProfiling public CompletableFuture<List<BuildRef>> getRunningBuilds(@Nullable String branch) {
        return supplyAsync(() -> getBuildsInState(null, branch, BuildRef.STATE_RUNNING), executor);
    }

    /** {@inheritDoc} */
    @Override
    @AutoProfiling public CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable String branch) {
        return supplyAsync(() -> getBuildsInState(null, branch, BuildRef.STATE_QUEUED), executor);
    }

    private List<BuildRef> getBuildsInState(
            @Nullable final String projectId,
            @Nullable final String branch,
            @Nonnull final String state,
            @Nullable final Integer sinceBuildId) {
        List<BuildRef> finished = getBuildHistory(projectId,
            UrlUtil.escape(branch),
            false,
            state,
            null,
            null,
            sinceBuildId);
        return finished.stream().filter(BuildRef::isNotCancelled).collect(Collectors.toList());
    }


    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected List<BuildRef> getBuildsInState(
            @Nullable final String projectId,
            @Nullable final String branch,
            @Nonnull final String state) {

        return getBuildsInState(projectId, branch, state, null);
    }

    /** {@inheritDoc} */
    @Override public String serverId() {
        return tcName;
    }

    private CompletableFuture<LogCheckTask> checkBuildLogNoCache(int buildId, ISuiteResults ctx) {
        final CompletableFuture<File> zipFut = downloadBuildLogZip(buildId);
        boolean dumpLastTest = ctx.hasSuiteIncompleteFailure();

        return zipFut.thenApplyAsync(zipFile -> runCheckForZippedLog(dumpLastTest, zipFile), executor);
    }

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    @NotNull protected LogCheckTask runCheckForZippedLog(boolean dumpLastTest, File zipFile) {
        LogCheckTask task = new LogCheckTask(zipFile);

        try {
            //get the zip file content
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry ze = zis.getNextEntry();    //get the zipped file list entry

                while (ze != null) {
                    BuildLogStreamChecker checker = task.createChecker();
                    checker.apply(zis, zipFile);
                    task.finalize(dumpLastTest);

                    ze = zis.getNextEntry();
                }
                zis.closeEntry();
            }
        }
        catch (IOException | UncheckedIOException e) {
            final String msg = "Failed to process ZIPed entry " + zipFile;

            System.err.println(msg);
            e.printStackTrace();

            logger.error(msg, e);
        }

        return task;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    @AutoProfiling
    public Users getUsers() {
        return getJaxbUsingHref("app/rest/latest/users", Users.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public User getUserByUsername(String username) {
        return getJaxbUsingHref("app/rest/latest/users/username:" + username, User.class);
    }

    public void setHttpConn(ITeamcityHttpConnection teamcityHttpConn) {
        this.teamcityHttpConn = teamcityHttpConn;
    }
}
