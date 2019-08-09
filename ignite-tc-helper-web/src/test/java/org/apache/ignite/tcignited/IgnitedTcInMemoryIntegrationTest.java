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
package org.apache.ignite.tcignited;

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.JAXBException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest;
import org.apache.ignite.ci.tcbot.issue.IssueDetectorTest;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.pure.BuildHistoryEmulator;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.jiraservice.IJiraIntegrationProvider;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.GuavaCachedModule;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.TcBotJsonConfig;
import org.apache.ignite.tcbot.engine.issue.EventTemplates;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IgniteStringCompactor;
import org.apache.ignite.tcbot.persistence.TcBotPersistenceModule;
import org.apache.ignite.tcbot.persistence.scheduler.DirectExecNoWaitScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.ProactiveFatBuildSync;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcignited.buildlog.ILogProductSpecific;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcservice.TeamcityServiceConnection;
import org.apache.ignite.tcservice.http.ITeamcityHttpConnection;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.conf.Project;
import org.apache.ignite.tcservice.model.conf.bt.BuildTypeFull;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.mute.Mutes;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.Triggered;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrences;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.apache.ignite.tcservice.model.vcs.Revision;
import org.apache.ignite.tcservice.model.vcs.Revisions;
import org.apache.ignite.tcservice.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static com.google.common.base.Preconditions.checkNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.ignite.ci.tcbot.issue.IssueDetectorTest.SRV_ID;
import static org.apache.ignite.tcbot.common.conf.TcBotWorkDir.ensureDirExist;
import static org.apache.ignite.tcbot.persistence.IgniteStringCompactor.STRINGS_CACHE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for ignite persistence
 */
public class IgnitedTcInMemoryIntegrationTest {
    /** Server Name for test. */
    public static final String APACHE = "apachetest";

    /** Test ignite port. */
    public static final int TEST_IGNITE_PORT = 64124;

    /** Ignite. */
    private static Ignite ignite;

    /**
     *
     */
    @BeforeClass
    public static void startIgnite() {
        IgniteConfiguration cfg = new IgniteConfiguration();
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
        int locPort = TEST_IGNITE_PORT;

        spi.setLocalPort(locPort);
        spi.setLocalPortRange(1);
        spi.setIpFinder(new TcHelperDb.LocalOnlyTcpDiscoveryIpFinder(locPort));

        cfg.setDiscoverySpi(spi);

        ignite = Ignition.start(cfg);
    }

    /**
     *
     */
    @AfterClass
    public static void stopIgnite() {
        if (ignite != null)
            ignite.close();
    }

    /**
     * Clear relevant ignite caches to avoid tests invluence to each other.
     */
    @Before
    public void clearIgniteCaches() {
        clearCache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);
        clearCache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);

        BuildRefCompacted.resetCached();
        TestCompactedV2.resetCached();
        TestCompactedMult.resetCached();
    }

    /**
     * @param cacheName Cache name to clear.
     */
    private void clearCache(String cacheName) {
        IgniteCache<Long, BuildRefCompacted> buildRefCache = ignite.cache(cacheName);

        if (buildRefCache != null)
            buildRefCache.clear();
    }

    @Test
    public void saveAndLoadBuildReference() throws IOException {
        ITeamcityHttpConnection http = Mockito.mock(ITeamcityHttpConnection.class);

        when(http.sendGet(anyString(), anyString())).thenAnswer(
            (invocationOnMock) -> {
                String url = invocationOnMock.getArgument(1);

                if (url.contains("app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000"))
                    return getClass().getResourceAsStream("/buildHistoryMasterPage2.xml");

                if (url.contains("app/rest/latest/builds?locator=defaultFilter:false"))
                    return getClass().getResourceAsStream("/buildHistoryMaster.xml");

                throw new FileNotFoundException(url);
            }
        );

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        module.overrideHttp(http);

        Injector injector = Guice.createInjector(module, new IgniteAndSchedulerTestModule());

        ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, creds());
        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);

        String buildTypeId = "IgniteTests24Java8_RunAll";
        String branchName = "<default>";
        List<BuildRefCompacted> hist = srv.getAllBuildsCompacted(buildTypeId, branchName);
        //todo mult branches including pull/4926/head

        assertTrue(!hist.isEmpty());

        for (BuildRefCompacted h : hist) {
            System.out.println(h);

            assertEquals(buildTypeId, h.buildTypeId(compactor));

            assertEquals("refs/heads/master", h.branchName(compactor));
        }

        ignite.cache(STRINGS_CACHE).forEach(
            (e) -> {
                System.out.println(e.getValue());
            }
        );
    }

    @Test
    public void saveAndLoadBuildTypes() throws IOException, JAXBException {
        final String projectId = "IgniteTests24Java8";
        final String runAll = projectId + "_RunAll";
        final String cluster = projectId + "_ActivateDeactivateCluster";
        final AtomicBoolean buildTypeRmv = new AtomicBoolean();
        final AtomicBoolean runAllRmv = new AtomicBoolean();
        final AtomicBoolean clusterRmv = new AtomicBoolean();

        ITeamcityHttpConnection http = Mockito.mock(ITeamcityHttpConnection.class);

        when(http.sendGet(anyString(), anyString())).thenAnswer(
            (invocationOnMock) -> {
                String url = invocationOnMock.getArgument(1);
                if (url.contains("app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000"))
                    return getClass().getResourceAsStream("/buildHistoryMasterPage2.xml");

                if (url.contains("app/rest/latest/builds?locator=defaultFilter:false"))
                    return getClass().getResourceAsStream("/buildHistoryMaster.xml");

                if (url.contains("app/rest/latest/projects/" + projectId))
                    return getClass().getResourceAsStream("/" + projectId +
                        (buildTypeRmv.get() ? "_v2" : "") + ".xml");

                if ((url.contains("app/rest/latest/buildTypes/id:" + runAll)) && !runAllRmv.get())
                    return getClass().getResourceAsStream("/" + runAll + ".xml");

                if ((url.contains("app/rest/latest/buildTypes/id:" + cluster)) && !clusterRmv.get())
                    return getClass().getResourceAsStream("/" + cluster + ".xml");

                throw new FileNotFoundException(url);
            }
        );

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        module.overrideHttp(http);

        Injector injector = Guice.createInjector(module, new IgniteAndSchedulerTestModule());

        ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, creds());
        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);

        TeamcityIgnitedImpl teamcityIgnited = (TeamcityIgnitedImpl)srv;
        teamcityIgnited.fullReindex();

        List<String> buildTypes = srv.getCompositeBuildTypesIdsSortedByBuildNumberCounter(projectId);

        assertEquals(buildTypes.size(), 1);

        assertEquals(buildTypes.get(0), runAll);

        List<BuildTypeRefCompacted> allBuildTypes = srv.getAllBuildTypesCompacted(projectId);

        assertEquals(allBuildTypes.size(), 2);

        buildTypeRmv.set(true);

        buildTypes = srv.getCompositeBuildTypesIdsSortedByBuildNumberCounter(projectId);

        assertTrue(buildTypes.isEmpty());

        assertTrue(srv.getBuildTypeRef(runAll).removed());

        clusterRmv.set(true);

        srv.getAllBuildTypesCompacted(projectId);

        assertTrue(srv.getBuildType(cluster).removed());

        runAllRmv.set(false);
        clusterRmv.set(false);
        buildTypeRmv.set(false);

        allBuildTypes = srv.getAllBuildTypesCompacted(projectId);

        assertEquals(allBuildTypes
            .stream()
            .filter(bt -> !srv.getBuildType(bt.id(compactor)).removed()).count(), 2);

        BuildType runAllRef = jaxbTestXml("/" + projectId + ".xml", Project.class).getBuildTypesNonNull()
            .stream()
            .filter(bt -> bt.getId().equals(runAll))
            .findFirst()
            .orElse(null);

        BuildType runAllRefFromCache = srv.getBuildTypeRef(runAll).toBuildTypeRef(compactor, srv.host());

        assertEquals(runAllRef, runAllRefFromCache);

        BuildTypeFull runAllFull = jaxbTestXml("/" + runAll + ".xml", BuildTypeFull.class);

        BuildTypeFull runAllFullFromCache = srv.getBuildType(runAll).toBuildType(compactor, srv.host());

        assertEquals(runAllFull, runAllFullFromCache);
    }

    @Test
    public void incrementalActualizationOfBuildsContainsQueued() throws IOException {
        ITeamcityHttpConnection http = Mockito.mock(ITeamcityHttpConnection.class);

        int queuedBuildIdx = 500;
        ArrayList<BuildRef> tcBuilds = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            BuildRef e = new BuildRef();
            e.state = i >= queuedBuildIdx ?
                (Math.random() * 2 > 1 ? BuildRef.STATE_QUEUED : BuildRef.STATE_RUNNING)
                : BuildRef.STATE_FINISHED;
            e.status = BuildRef.STATUS_SUCCESS;
            e.buildTypeId = "IgniteTests24Java8_RunAll";
            e.branchName = "refs/heads/master";
            e.setId(i + 50000);
            tcBuilds.add(e);
        }

        BuildHistoryEmulator emulator = new BuildHistoryEmulator(tcBuilds);

        when(http.sendGet(anyString(), anyString())).thenAnswer(
            (invocationOnMock) -> {
                String url = invocationOnMock.getArgument(1);

                InputStream stream = emulator.handleUrl(url);

                if (stream != null)
                    return stream;

                throw new FileNotFoundException(url);
            }
        );

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        module.overrideHttp(http);

        Injector injector = Guice.createInjector(module, new IgniteAndSchedulerTestModule());

        ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, creds());
        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);

        TeamcityIgnitedImpl teamcityIgnited = (TeamcityIgnitedImpl)srv;
        teamcityIgnited.fullReindex();
        String buildTypeId = "IgniteTests24Java8_RunAll";
        String branchName = "<default>";
        List<String> statues = srv.getAllBuildsCompacted(buildTypeId, branchName)
            .stream().map((t) -> t.state(compactor)).distinct().collect(Collectors.toList());
        System.out.println("Before " + statues);

        for (int i = queuedBuildIdx; i < tcBuilds.size(); i++)
            tcBuilds.get(i).state = BuildRef.STATE_FINISHED;

        teamcityIgnited.actualizeRecentBuildRefs();

        List<BuildRefCompacted> hist = srv.getAllBuildsCompacted(buildTypeId, branchName);

        assertTrue(!hist.isEmpty());

        for (BuildRefCompacted h : hist) {
            assertEquals(buildTypeId, h.buildTypeId(compactor));

            assertEquals("refs/heads/master", h.branchName(compactor));

            assertTrue("Build " + h + " is expected to be finished", h.isFinished(compactor));
        }

        statues = hist.stream().map((t) -> t.state(compactor)).distinct().collect(Collectors.toList());

        System.out.println("After " + statues);
    }

    /**
     *
     */
    @NotNull public ITcBotUserCreds creds() {
        ITcBotUserCreds mock = Mockito.mock(ITcBotUserCreds.class);

        when(mock.hasAccess(anyString())).thenReturn(true);
        when(mock.getUser(anyString())).thenReturn("mtcga");
        when(mock.getPassword(anyString())).thenReturn("123");

        return mock;
    }

    @Test
    public void testMutesXml() throws JAXBException, IOException {
        Mutes mutes = jaxbTestXml("/mutes.xml", Mutes.class);

        assertNotNull(mutes);

        Set<MuteInfo> infos = mutes.getMutesNonNull();

        assertNotNull(infos);
        assertEquals(100, infos.size());

        MuteInfo mute = findMute(4832, infos);

        assertEquals("20171215T185123+0300", mute.assignment.muteDate);
        assertEquals("https://issues.apache.org/jira/browse/IGNITE-1090", mute.assignment.text);
        assertEquals("IgniteTests24Java8", mute.scope.project.id);

        mute = findMute(6919, infos);

        assertNotNull(mute.scope.buildTypes);
        assertEquals(1, mute.scope.buildTypes.size());
        assertEquals("IgniteTests24Java8_DiskPageCompressions", mute.scope.buildTypes.get(0).getId());
        assertNotNull(mute.target.tests);
        assertEquals(1, mute.target.tests.size());
        assertEquals("-5875314334924226234", mute.target.tests.get(0).id);
    }

    /**
     * @param id Mute id.
     * @param infos Collection of mutes.
     * @return Mute with speciified id.
     * @throws IllegalArgumentException if mute wasn't found.
     */
    private MuteInfo findMute(int id, Collection<MuteInfo> infos) {
        for (MuteInfo info : infos) {
            if (info.id != id)
                continue;

            return info;
        }

        throw new IllegalArgumentException("Mute not found [id=" + id + ']');
    }

    @Test
    public void testFatBuild() throws JAXBException, IOException {
        Build refBuild = jaxbTestXml("/build.xml", Build.class);
        TestOccurrencesFull testsRef = jaxbTestXml("/testList.xml", TestOccurrencesFull.class);
        ProblemOccurrences problemsList = jaxbTestXml("/problemList.xml", ProblemOccurrences.class);
        Statistics statistics = jaxbTestXml("/statistics.xml", Statistics.class);
        ChangesList changesList = jaxbTestXml("/changeList.xml", ChangesList.class);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IStringCompactor.class).to(IgniteStringCompactor.class).in(new SingletonScope());
                bind(IDataSourcesConfigSupplier.class).toInstance(Mockito.mock(IDataSourcesConfigSupplier.class));
                bind(ILogProductSpecific.class).toInstance(Mockito.mock(ILogProductSpecific.class));
            }
        });

        FatBuildDao stor = injector.getInstance(FatBuildDao.class);
        stor.init();

        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(APACHE);
        List<TestOccurrencesFull> occurrences = Collections.singletonList(testsRef);
        FatBuildCompacted buildCompacted = stor.saveBuild(srvIdMaskHigh, refBuild.getId(), refBuild, occurrences,
            problemsList.getProblemsNonNull(), statistics, changesList, null);
        assertNotNull(buildCompacted);

        FatBuildCompacted fatBuild = stor.getFatBuild(srvIdMaskHigh, 2153237);

        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);

        Build actBuild = fatBuild.toBuild(compactor);

        saveTmpFile(refBuild, "src/test/tmp/buildRef.xml");
        saveTmpFile(actBuild, "src/test/tmp/buildAct.xml");

        TestOccurrencesFull testsAct = fatBuild.getTestOcurrences(compactor);
        saveTmpFile(testsRef, "src/test/tmp/testListRef.xml");
        saveTmpFile(testsAct, "src/test/tmp/testListAct.xml");

        assertEquals(refBuild.getId(), actBuild.getId());
        assertEquals(refBuild.status(), actBuild.status());
        assertEquals(refBuild.state(), actBuild.state());
        assertEquals(refBuild.buildTypeId(), actBuild.buildTypeId());
        assertEquals(refBuild.getStartDate(), actBuild.getStartDate());
        assertEquals(refBuild.getFinishDate(), actBuild.getFinishDate());

        assertParameter(refBuild, actBuild, "env.JAVA_HOME");
        assertParameter(refBuild, actBuild, "reverse.dep.*.env.JAVA_HOME");
        assertNotNull(refBuild.parameter(ITeamcity.TCBOT_TRIGGER_TIME));
        assertNull(actBuild.parameter(ITeamcity.TCBOT_TRIGGER_TIME));

        final Triggered refTrig = refBuild.getTriggered();
        final Triggered actTrig = actBuild.getTriggered();
        assertNotNull(refTrig);
        assertNotNull(actTrig);

        assertNotNull(refTrig.getUser());
        assertNotNull(actTrig.getUser());

        assertEquals(refTrig.getUser().username, actTrig.getUser().username);
        assertEquals(refTrig.getBuild().getId(), actTrig.getBuild().getId());

        BuildType refBt = refBuild.getBuildType();
        BuildType actBt = actBuild.getBuildType();
        assertEquals(refBt.getName(), actBt.getName());
        assertEquals(refBt.getProjectId(), actBt.getProjectId());
        assertEquals(refBt.getId(), actBt.getId());

        Set<String> testNamesAct = new TreeSet<>();
        testsAct.getTests().forEach(testOccurrence -> testNamesAct.add(testOccurrence.name));

        Set<String> testNamesRef = new TreeSet<>();
        testsRef.getTests().forEach(testOccurrence -> testNamesRef.add(testOccurrence.name));
        assertEquals(testNamesRef, testNamesAct);

        final List<ProblemOccurrence> problems = buildCompacted.problems(compactor);
        assertEquals(2, problems.size());

        assertTrue(problems.stream().anyMatch(ProblemOccurrence::isFailedTests));
        assertTrue(problems.stream().anyMatch(ProblemOccurrence::isExitCode));
        assertTrue(problems.stream().noneMatch(ProblemOccurrence::isJvmCrash));

        Long duration = buildCompacted.buildDuration(compactor);
        assertNotNull(duration);
        assertTrue(duration > 10000L);

        int[] ch = buildCompacted.changes();

        assertEquals(6, ch.length);

        final Revisions refRevisions = refBuild.getRevisions();
        final Revisions actRevisions = actBuild.getRevisions();
        assertNotNull(refRevisions);
        assertNotNull(actRevisions);

        Set<String> refVersions = refRevisions.revisions().stream().map(Revision::version).collect(Collectors.toSet());
        Set<String> actVersions = actRevisions.revisions().stream().map(Revision::version).collect(Collectors.toSet());

        assertEquals(refVersions, actVersions);

        Revision refRev0 = refRevisions.revisions().get(0);
        Revision actRev0 = actRevisions.revisions().get(0);

        assertEquals(refRev0.vcsBranchName(), actRev0.vcsBranchName());
        assertEquals(refRev0.vcsRootInstance().id(), actRev0.vcsRootInstance().id());
        assertEquals(refRev0.vcsRootInstance().vcsRootId(), actRev0.vcsRootInstance().vcsRootId());


    }

    public void assertParameter(Build refBuild, Build actBuild, String parmKey) {
        assertNotNull(refBuild.parameter(parmKey));
        assertEquals(refBuild.parameter(parmKey), actBuild.parameter(parmKey));
    }

    private void saveTmpFile(Object obj, String name) throws IOException, JAXBException {
        ensureDirExist(new File(name).getParentFile());

        try (FileWriter writer = new FileWriter(name)) {
            writer.write(XmlUtil.save(obj));
        }
    }

    private <E> E jaxbTestXml(String ref, Class<E> cls) throws IOException, JAXBException {
        E refBuild;
        try (InputStream stream = getClass().getResourceAsStream(ref)) {
            refBuild = XmlUtil.load(cls, new InputStreamReader(stream));
        }
        return refBuild;
    }

    @Test
    public void testRunHistSaveLoad() {
        Injector injector = Guice.createInjector(new TeamcityIgnitedModule(), new IgniteAndSchedulerTestModule());

        injector.getInstance(BuildStartTimeStorage.class).init();
        final IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final String srvId = "apache";
        final String btId = "RunAll";
        final String branch = ITeamcity.DEFAULT;

        final PrChainsProcessorTest tst = new PrChainsProcessorTest();
        tst.initBuildChainAndMasterHistory(c, btId, branch);

        final Map<Integer, FatBuildCompacted> buildsMap = tst.apacheBuilds();

        final ITeamcityIgnitedProvider inst = injector.getInstance(ITeamcityIgnitedProvider.class);
        final ITeamcityIgnited srv = inst.server(srvId, Mockito.mock(ITcBotUserCreds.class));
        final IRunHistory testRunHist = srv.getTestRunHist(
            c.getStringId(PrChainsProcessorTest.TEST_FLAKY_IN_MASTER),
            c.getStringId(PrChainsProcessorTest.CACHE_1),
            c.getStringId(branch));

        // todo register builds buildsMap somehow in injector
        // assertNotNull(testRunHist);
        // assertEquals(0.5, testRunHist.getFailRate(), 0.1);

        final ISuiteRunHistory cache1Hist = srv.getSuiteRunHist(c.getStringId(PrChainsProcessorTest.CACHE_1)
            , c.getStringId(branch));

        assertNotNull(cache1Hist);
        // todo register builds somehow in injector
        //assertEquals(1.0, cache1Hist.self().getFailRate(), 0.1);
        //assertEquals(0.18, cache1Hist.self().getCriticalFailRate(), 0.05);
    }

    @Test
    public void testHistoryBackgroundUpdateWorks() {
        Injector injector = Guice.createInjector(new TeamcityIgnitedModule(), new IgniteAndSchedulerTestModule());

        injector.getInstance(BuildStartTimeStorage.class).init();

        final String srvId = "apache";
        final String btId = "RunAll";
        final String branch = ITeamcity.DEFAULT;

        final ITeamcityIgnitedProvider inst = injector.getInstance(ITeamcityIgnitedProvider.class);
        final ITeamcityIgnited srv = inst.server(srvId, Mockito.mock(ITcBotUserCreds.class));

        FatBuildDao fatBuildDao = injector.getInstance(FatBuildDao.class);
        fatBuildDao.init();

        BuildRefDao buildRefDao = injector.getInstance(BuildRefDao.class);
        buildRefDao.init();

        final IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final PrChainsProcessorTest tst = new PrChainsProcessorTest();
        tst.initBuildChainAndMasterHistory(c, btId, branch);

        final Map<Integer, FatBuildCompacted> buildsMap = tst.apacheBuilds();

        buildsMap.forEach((id, build) -> {
            int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
            fatBuildDao.putFatBuild(srvIdMaskHigh, id, build);
            buildRefDao.save(srvIdMaskHigh, new BuildRefCompacted(build));
        });

        final IRunHistory testRunHist = srv.getTestRunHist(c.getStringId(PrChainsProcessorTest.TEST_FLAKY_IN_MASTER),
            c.getStringId(PrChainsProcessorTest.CACHE_1),
            c.getStringId(branch));

        assertNotNull(testRunHist);
        assertEquals(0.5, testRunHist.getFailRate(), 0.1);
    }

    @Test
    public void testQueuedBuildsRemoved() {
        TeamcityIgnitedModule module = new TeamcityIgnitedModule();
        module.overrideHttp((basicAuthTok, url) -> {
            throw new FileNotFoundException(url);
        });
        Injector injector = Guice.createInjector(module, new IgniteAndSchedulerTestModule());

        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        BuildRefDao buildRefDao = injector.getInstance(BuildRefDao.class).init();
        FatBuildDao fatBuildDao = injector.getInstance(FatBuildDao.class).init();

        int buildIdQ = 1000042;
        BuildRef refQ = new BuildRef();
        refQ.buildTypeId = "Testbuild";
        refQ.branchName = ITeamcity.REFS_HEADS_MASTER;
        refQ.state = BuildRef.STATE_QUEUED;
        refQ.setId(buildIdQ);

        int buildIdR = 1000043;
        BuildRef refR = new BuildRef();
        refR.buildTypeId = "Testbuild";
        refR.branchName = ITeamcity.REFS_HEADS_MASTER;
        refR.state = BuildRef.STATE_RUNNING;
        refR.setId(buildIdR);

        String srvCode = APACHE;
        int srvIdInt = ITeamcityIgnited.serverIdToInt(srvCode);
        final TeamcityServiceConnection srvConn = injector.getInstance(TeamcityServiceConnection.class);
        srvConn.init(srvCode);

        buildRefDao.saveChunk(srvIdInt, Lists.newArrayList(refQ, refR));

        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdInt);
        assertFalse(checkNotNull(running).isEmpty());

        System.out.println("Running builds (before sync): " + printRefs(c, running));

        ProactiveFatBuildSync buildSync = injector.getInstance(ProactiveFatBuildSync.class);
        buildSync.ensureActualizationRequested(srvCode, srvConn);

        FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(srvIdInt, buildIdQ);
        System.out.println(fatBuild);

        assertNotNull(fatBuild);
        assertTrue(fatBuild.isFakeStub());

        assertTrue(fatBuild.isCancelled(c));

        List<BuildRefCompacted> running2 = buildRefDao.getQueuedAndRunning(srvIdInt);
        System.out.println("Running builds (after sync): " + printRefs(c, running2));
        assertTrue(checkNotNull(running2).isEmpty());

        // Now we have 2 fake stubs, retry to actualize
        buildRefDao.saveChunk(srvIdInt, Lists.newArrayList(refQ, refR));

        List<BuildRefCompacted> running3 = buildRefDao.getQueuedAndRunning(srvIdInt);
        System.out.println("Running builds (before with fake builds): " + printRefs(c, running3));
        assertFalse(checkNotNull(running3).isEmpty());

        putOldFashionFakeBuild(c, fatBuildDao, buildIdQ, srvIdInt);
        putOldFashionFakeBuild(c, fatBuildDao, buildIdR, srvIdInt);

        buildSync.ensureActualizationRequested(srvCode, srvConn);

        List<BuildRefCompacted> running4 = buildRefDao.getQueuedAndRunning(srvIdInt);
        System.out.println("Running builds (before with fake builds): " + printRefs(c, running4));
        assertTrue(checkNotNull(running4).isEmpty());
    }

    public void putOldFashionFakeBuild(IStringCompactor c, FatBuildDao fatBuildDao, int buildId, int srvIdInt) {
        FatBuildCompacted fb = fatBuildDao.getFatBuild(srvIdInt, buildId);

        fb.fillFieldsFromBuildRef(c, new BuildRef());

        fatBuildDao.putFatBuild(srvIdInt, buildId, fb);

        assertNull(fatBuildDao.getFatBuild(srvIdInt, buildId).state(c));
    }

    @NotNull public List<BuildRef> printRefs(IStringCompactor c, List<BuildRefCompacted> running2) {
        return running2.stream().map(bref -> bref.toBuildRef(c)).collect(Collectors.toList());
    }

    @Test
    public void testCachesInvalidation() {
        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        Injector injector = Guice.createInjector(module, new GuavaCachedModule(), new IgniteAndSchedulerTestModule());

        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        BuildRefDao storage = injector.getInstance(BuildRefDao.class);
        storage.init();

        int srvId = ITeamcityIgnited.serverIdToInt("apache");
        int branch = c.getStringId("myBranch");
        AtomicInteger idGen = new AtomicInteger();
        int buildTypeId = c.getStringId("myBuildType");
        BuildRefCompacted ref = new BuildRefCompacted().withId(idGen.incrementAndGet()).state(0).status(2).branchName(branch).buildTypeId(buildTypeId);

        storage.save(srvId, ref);

        Set<Integer> branchList = Collections.singleton(branch);
        assertEquals(1, storage.getAllBuildsCompacted(srvId, buildTypeId, branchList).size());

        storage.save(srvId, ref.withId(idGen.incrementAndGet()));
        assertEquals(2, storage.getAllBuildsCompacted(srvId, buildTypeId, branchList).size());

        storage.save(srvId, ref.withId(idGen.incrementAndGet()).branchName(c.getStringId("SomeUnrelatedBranch")));
        assertEquals(2, storage.getAllBuildsCompacted(srvId, buildTypeId, branchList).size());

        storage.save(srvId, ref.withId(idGen.incrementAndGet()).branchName(branch));
        int present = 3;
        assertEquals(present, storage.getAllBuildsCompacted(srvId, buildTypeId, branchList).size());

        LongAdder savedCnt = new LongAdder();
        ExecutorService svc = Executors.newFixedThreadPool(10);
        List<Future<Void>> futures = new ArrayList<>();
        int tasks = 100;
        int buildPerTask = 100;
        for (int i = 0; i < tasks; i++) {
            int finalI = i;
            futures.add(svc.submit(() -> {
                List<BuildRefCompacted> collect = Stream.generate(() -> new BuildRefCompacted()
                    .withId(idGen.incrementAndGet())
                    .state(10000 + finalI).status(1032)
                    .branchName(branch)
                    .buildTypeId(buildTypeId)).limit(buildPerTask).collect(Collectors.toList());

                collect.forEach(bRef -> {
                    storage.save(srvId, bRef);
                    savedCnt.add(1);
                });

                return null;
            }));
        }

        long ms = System.currentTimeMillis();
        do {
            int saved = savedCnt.intValue() + present;
            int size = storage.getAllBuildsCompacted(srvId, buildTypeId, branchList).size();

            System.out.println("Builds available " + size + " save completed for " + saved);
            assertTrue(size >= saved);

            if (savedCnt.intValue() >= tasks * buildPerTask)
                break;

            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        while (System.currentTimeMillis() - ms < 5000);

        FutureUtil.getResults(futures);
        svc.shutdown();
    }

    @Test
    public void testTestHistoryPropagation() {
        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        Injector injector = Guice.createInjector(module, new GuavaCachedModule(), new IgniteAndSchedulerTestModule());

        String chainId = TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;
        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        String testUnmuted = "testUnmuted";
        Map<String, String> pds1Hist = new TreeMap<String, String>() {
            {
                put(testUnmuted, "66666611111");
                put("testOk", "      0000");
            }
        };
        String testFlakyStableFailure = "testFlakyStableFailure";

        Map<String, String> pds2Hist = new TreeMap<String, String>() {
            {
                put("testFailedShoudlBeConsideredAsFlaky", "0000011111");
                put(testFlakyStableFailure, "0000011111111111");
            }
        };

        Map<Integer, FatBuildCompacted> builds = new HashMap<>();
        String suite1 = IssueDetectorTest.PDS_1;
        String suite2 = IssueDetectorTest.PDS_2;
        IssueDetectorTest.emulateHistory(builds, chainId, c, suite1, pds1Hist, suite2, pds2Hist);

        BuildRefDao buildRefDao = injector.getInstance(BuildRefDao.class).init();

        FatBuildDao fatBuildDao = injector.getInstance(FatBuildDao.class).init();

        injector.getInstance(SuiteInvocationHistoryDao.class).init();
        injector.getInstance(BuildStartTimeStorage.class).init();

        int srvId = ITeamcityIgnited.serverIdToInt(IssueDetectorTest.SRV_ID);
        builds.forEach((k, v) -> {
            buildRefDao.save(srvId, new BuildRefCompacted(v));
            fatBuildDao.putFatBuild(srvId, k, v);
        });

        HistoryCollector histCollector = injector.getInstance(HistoryCollector.class);

        IRunHistory hist = histCollector.getTestRunHist(SRV_ID,
            c.getStringId(testUnmuted),
            c.getStringId(suite1),
            c.getStringId(ITeamcity.DEFAULT));

        assertNotNull(hist);

        assertEquals(Arrays.asList(4, 4, 4, 4, 4, 6, 6, 6, 6, 6, 6, 1, 1, 1, 1, 1), hist.getLatestRunResults());

        assertEquals(0, hist.getCriticalFailuresCount());

        assertFalse(hist.isFlaky());
        assertEquals(1.0, hist.getFailRate(), 0.05);

        IRunHistory histFailedFlaky = histCollector.getTestRunHist(SRV_ID,
            c.getStringId(testFlakyStableFailure),
            c.getStringId(suite2),
            c.getStringId(ITeamcity.DEFAULT));

        assertTrue(histFailedFlaky.isFlaky());

        Integer integer = histFailedFlaky.detectTemplate(EventTemplates.newFailureForFlakyTest);
        assertNotNull(integer);
    }

    /**
     *
     */
    private static class IgniteAndSchedulerTestModule extends AbstractModule {
        /** {@inheritDoc} */
        @Override protected void configure() {
            bind(Ignite.class).toInstance(ignite);
            bind(IScheduler.class).to(DirectExecNoWaitScheduler.class).in(new SingletonScope());

            final IJiraIntegrationProvider jiraProv = Mockito.mock(IJiraIntegrationProvider.class);
            bind(IJiraIntegrationProvider.class).toInstance(jiraProv);

            ITcBotConfig cfg = Mockito.mock(ITcBotConfig.class);

            ITcServerConfig tcCfg = mock(ITcServerConfig.class);
            when(tcCfg.logsDirectory()).thenReturn("logs");
            when(tcCfg.host()).thenReturn("http://ci.ignite.apache.org/");
            when(tcCfg.trustedSuites()).thenReturn(new ArrayList<>());
            when(cfg.getTeamcityConfig(anyString())).thenReturn(tcCfg);
            when(cfg.getTrackedBranches()).thenReturn(new TcBotJsonConfig());

            bind(ITcBotConfig.class).toInstance(cfg);
            bind(IDataSourcesConfigSupplier.class).toInstance(cfg);

            install(new TcBotPersistenceModule());
        }
    }
}
