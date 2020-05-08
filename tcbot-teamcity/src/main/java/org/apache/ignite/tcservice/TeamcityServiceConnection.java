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

package org.apache.ignite.tcservice;

import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.exeption.ServiceConflictException;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.util.HttpUtil;
import org.apache.ignite.tcservice.http.ITeamcityHttpConnection;
import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.agent.AgentsRef;
import org.apache.ignite.tcservice.model.changes.Change;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.conf.Project;
import org.apache.ignite.tcservice.model.conf.ProjectsList;
import org.apache.ignite.tcservice.model.conf.bt.BuildTypeFull;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.hist.Builds;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.mute.Mutes;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrences;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcservice.model.user.Users;
import org.apache.ignite.tcservice.util.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for access to Teamcity REST API without any caching.
 *
 * See more info about API
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 * https://developer.github.com/v3/
 */
public class TeamcityServiceConnection implements ITeamcity {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TeamcityServiceConnection.class);

    /** TeamCity authorization token. */
    private String basicAuthTok;

    /** Teamcity http connection. */
    @Inject private ITeamcityHttpConnection teamcityHttpConn;

    @Inject private IDataSourcesConfigSupplier cfg;

    private String srvCode;

    public void init(@Nullable String srvCode) {
        this.srvCode = srvCode;
    }

    @Override public ITcServerConfig config() {
        return cfg.getTeamcityConfig(this.srvCode);
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

    /** {@inheritDoc} */
    @AutoProfiling
    public File downloadAndCacheBuildLog(int buildId) {
        String buildIdStr = Integer.toString(buildId);
        File file = new File(logsDir(), "build" + buildIdStr + ".log.zip");

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
    }

    private static File resolveLogs(File workDir, String logsProp) {
        final File logsDirFileConfigured = new File(logsProp);
        return logsDirFileConfigured.isAbsolute() ? logsDirFileConfigured : new File(workDir, logsProp);
    }

    private File logsDir() {
        File logsDirFile = resolveLogs(
                TcBotWorkDir.resolveWorkDir(),
                config().logsDirectory());

        return TcBotWorkDir.ensureDirExist(logsDirFile);
    }


    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Build triggerBuild(
        String buildTypeId,
        @Nonnull String branchName,
        boolean cleanRebuild,
        boolean queueAtTop,
        @Nullable Map<String, Object> buildParms,
        String freeTextComments) {
        String triggeringOptions =
            " <triggeringOptions" +
                " cleanSources=\"" + cleanRebuild + "\"" +
                " rebuildAllDependencies=\"" + cleanRebuild + "\"" +
                " queueAtTop=\"" + queueAtTop + "\"" +
                "/>\n";

        String comments = " <comment><text>" +
            Strings.nullToEmpty(freeTextComments) + ", " +
            "Build triggered from Ignite TC Bot" +
            " [cleanRebuild=" + cleanRebuild + ", top=" + queueAtTop + "]" +
            "</text></comment>\n";

        Map<String, Object> props = new HashMap<>();

        if (buildParms != null)
            props.putAll(buildParms);

        props.put(ITeamcity.TCBOT_TRIGGER_TIME, System.currentTimeMillis()); //

        StringBuilder sb = new StringBuilder();
        sb.append("<build branchName=\"").append(XmlUtil.xmlEscapeText(branchName)).append("\">\n");
        sb.append(" <buildType id=\"").append(buildTypeId).append("\"/>\n");
        sb.append(comments);
        sb.append(triggeringOptions);
        sb.append(" <properties>\n");

        props.forEach((k, v) -> {
            sb.append("  <property name=\"").append(k).append("\"");
            sb.append(" value=\"").append(XmlUtil.xmlEscapeText(Objects.toString(v))).append("\"/>\n");
        });

        sb.append(" </properties>\n");
        sb.append("</build>");

        String url = host() + "app/rest/buildQueue";

        try {
            logger.info("Triggering build: buildTypeId={}, branchName={}, cleanRebuild={}, queueAtTop={}, buildParms={}",
                buildTypeId, branchName, cleanRebuild, queueAtTop, props);

            String body = sb.toString();

            if (logger.isDebugEnabled())
                logger.debug("(TRIGGER REQUEST):\n" + body);

            try (StringReader reader = new StringReader(HttpUtil.sendPostAsString(basicAuthTok, url, body))) {
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

    /** {@inheritDoc} */
    @Override public List<Project> getProjects() {
        return sendGetXmlParseJaxb(host() + "app/rest/latest/projects", ProjectsList.class).projects();
    }

    /** {@inheritDoc} */
    @Override public List<BuildType> getBuildTypes(String projectId) {
        return sendGetXmlParseJaxb(host() + "app/rest/latest/projects/" + projectId, Project.class)
            .getBuildTypesNonNull();
    }

    /**
     * @param url Url.
     * @param rootElem Root elem.
     *
     * @throws UncheckedIOException caused by FileNotFoundException - If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     * @throws UncheckedIOException in case communication failed.
     */
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
    @Override public String serverCode() {
        return srvCode;
    }


    /**
     *
     * @throws RuntimeException in case loading failed. See details in {@link ITeamcityConn}.
     */
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
    @Nonnull
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
