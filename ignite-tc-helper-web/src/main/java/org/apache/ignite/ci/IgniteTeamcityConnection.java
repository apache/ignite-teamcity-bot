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
import org.apache.ignite.ci.analysis.ISuiteResults;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.LogCheckTask;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.logs.BuildLogStreamChecker;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.ITcServerConfig;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.agent.AgentsRef;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.ProjectsList;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.hist.Builds;
import org.apache.ignite.ci.tcmodel.mute.MuteInfo;
import org.apache.ignite.ci.tcmodel.mute.Mutes;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.tcmodel.user.Users;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.ci.util.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.SortedSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @Inject private ITcBotConfig config;

    private String tcName;

    /** Build logger processing running. */
    private ConcurrentHashMap<Integer, CompletableFuture<LogCheckTask>> buildLogProcessingRunning = new ConcurrentHashMap<>();

    public Executor getExecutor() {
        return executor;
    }

    /** {@inheritDoc} */
    @Override public void init(@Nullable String tcName) {
        this.tcName = tcName;

        ITcServerConfig tcCfg = this.config.getTeamcityConfig(tcName);
        final Properties props = tcCfg.properties();

        this.host = tcCfg.host();

        try {
            if (!Strings.isNullOrEmpty(props.getProperty(HelperConfig.USERNAME))
                    && props.getProperty(HelperConfig.ENCODED_PASSWORD) != null)
                setAuthToken(HelperConfig.prepareBasicHttpAuthToken(props, "TC Config"));
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to set credentials", e);
        }
        final File workDir = HelperConfig.resolveWorkDir();
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
            String url = host() + "downloadBuildLog.html" + "?buildId=" + buildIdStr + "&archived=true";

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
            @NotNull @Nonnull String branchName,
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

        String url = host() + "app/rest/buildQueue";

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

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public ProblemOccurrences getProblems(int buildId) {
        return getJaxbUsingHref("app/rest/latest/problemOccurrences" +
                "?locator=build:(id:" + buildId + ")" +
                "&fields=problemOccurrence(id,type,identity,href,details,build(id))", ProblemOccurrences.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Statistics getStatistics(int buildId) {
        return getJaxbUsingHref("app/rest/latest/builds/id:" + buildId + "/statistics", Statistics.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public ChangesList getChangesList(int buildId) {
        String href = "app/rest/latest/changes" +
                "?locator=build:(id:" + + buildId +")" +
                "&fields=change(id)";

        return getJaxbUsingHref(href, ChangesList.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Change getChange(int changeId) {
        String href = "app/rest/latest/changes/id:" + +changeId;

        return getJaxbUsingHref(href, Change.class);
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
     * @return Normalized Host address, ends with '/'.
     */
    @Override public String host() {
        return host;
    }

    /** {@inheritDoc} */
    @Override public List<Project> getProjects() {
        return sendGetXmlParseJaxb(host() + "app/rest/latest/projects", ProjectsList.class).projects();
    }

    /** {@inheritDoc} */
    @Override public List<BuildType> getBuildTypes(String projectId) {
        return sendGetXmlParseJaxb(host() + "app/rest/latest/projects/" + projectId, Project.class)
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

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public BuildTypeFull getBuildType(String buildTypeId) {
        return sendGetXmlParseJaxb(host() + "app/rest/latest/buildTypes/id:" +
            buildTypeId, BuildTypeFull.class);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Build getBuild(int buildId) {
        return getJaxbUsingHref("app/rest/latest/builds/id:" + buildId, Build.class);
    }

    /**
     * @param href Href.
     * @param elem Element class.
     */
    private <T> T getJaxbUsingHref(String href, Class<T> elem) {
        return sendGetXmlParseJaxb(host() + (href.startsWith("/") ? href.substring(1) : href), elem);
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

    /**
     * @param teamcityHttpConn Teamcity http connection.
     */
    public void setHttpConn(ITeamcityHttpConnection teamcityHttpConn) {
        this.teamcityHttpConn = teamcityHttpConn;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getBuildRefsPage(String fullUrl, AtomicReference<String> outNextPage) {
        String relPath = "app/rest/latest/builds?locator=defaultFilter:false";
        String relPathSelected = Strings.isNullOrEmpty(fullUrl) ? relPath : fullUrl;
        String url = host() + (relPathSelected.startsWith("/") ? relPathSelected.substring(1) : relPathSelected);
        Builds builds = sendGetXmlParseJaxb(url, Builds.class);

        outNextPage.set(Strings.emptyToNull(builds.nextHref()));

        return builds.getBuildsNonNull();
    }

    /** {@inheritDoc} */
    @Override public SortedSet<MuteInfo> getMutesPage(String buildTypeId, String fullUrl, AtomicReference<String> nextPage) {
        String relPath = "app/rest/mutes?locator=project:(id:" + buildTypeId + ')';
        String relPathSelected = Strings.isNullOrEmpty(fullUrl) ? relPath : fullUrl;
        String url = host() + (relPathSelected.startsWith("/") ? relPathSelected.substring(1) : relPathSelected);

        Mutes mutes = sendGetXmlParseJaxb(url, Mutes.class);

        nextPage.set(Strings.emptyToNull(mutes.nextHref()));

        return mutes.getMutesNonNull();
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public TestOccurrencesFull getTestsPage(int buildId, @Nullable String href, boolean testDtls) {
        String relPathSelected = Strings.isNullOrEmpty(href) ? testsStartHref(buildId, testDtls) : href;
        String url = host() + (relPathSelected.startsWith("/") ? relPathSelected.substring(1) : relPathSelected);
        return sendGetXmlParseJaxb(url, TestOccurrencesFull.class);
    }

    /**
     * @param buildId Build id.
     * @param testDtls request test details string
     */
    @NotNull
    private String testsStartHref(int buildId, boolean testDtls) {
        String fieldList = "id,name," +
            (testDtls ? "details," : "") +
            "status,duration,muted,currentlyMuted,currentlyInvestigated,ignored,test(id),build(id)";

        return "app/rest/latest/testOccurrences?locator=build:(id:" +
            buildId + ")" +
            "&fields=testOccurrence(" + fieldList + ")" +
            "&count=1000)";
    }
}
