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
package org.apache.ignite.ci.teamcity.ignited;

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBException;

import com.google.inject.internal.SingletonScope;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.scheduler.DirectExecNoWaitSheduler;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.di.scheduler.NoOpSheduler;
import org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProactiveFatBuildSync;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompactedDao;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync;
import org.apache.ignite.ci.teamcity.pure.BuildHistoryEmulator;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.teamcity.restcached.ITcServerFactory;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
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
import static org.apache.ignite.ci.HelperConfig.ensureDirExist;
import static org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.STRINGS_CACHE;
import static org.mockito.ArgumentMatchers.anyString;
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

                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000"))
                    return getClass().getResourceAsStream("/buildHistoryMasterPage2.xml");

                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false"))
                    return getClass().getResourceAsStream("/buildHistoryMaster.xml");

                throw new FileNotFoundException(url);
            }
        );

        TeamcityIgnitedModule module = new TeamcityIgnitedModule();

        module.overrideHttp(http);

        Injector injector = Guice.createInjector(module, new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IScheduler.class).to(DirectExecNoWaitSheduler.class);
            }
        });

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
                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000"))
                    return getClass().getResourceAsStream("/buildHistoryMasterPage2.xml");

                if (url.contains("/app/rest/latest/builds?locator=defaultFilter:false"))
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

        Injector injector = Guice.createInjector(module, new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IScheduler.class).to(DirectExecNoWaitSheduler.class);
            }
        });

        ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, creds());
        IStringCompactor compactor = injector.getInstance(IStringCompactor.class);

        TeamcityIgnitedImpl teamcityIgnited = (TeamcityIgnitedImpl)srv;
        teamcityIgnited.fullReindex();

        List<String> buildTypes = srv.getCompositeBuildTypesIdsSortedByBuildNumberCounter(projectId);

        assertEquals(buildTypes.size(),1);

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

        BuildType runAllRefFromCache = srv.getBuildTypeRef(runAll).toBuildTypeRef(compactor);

        assertEquals(runAllRef, runAllRefFromCache);

        BuildTypeFull runAllFull = jaxbTestXml("/" + runAll + ".xml", BuildTypeFull.class);

        BuildTypeFull runAllFullFromCache = srv.getBuildType(runAll).toBuildType(compactor);

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

        Injector injector = Guice.createInjector(module, new AbstractModule() {
            @Override protected void configure() {
                bind(Ignite.class).toInstance(ignite);
                bind(IScheduler.class).to(NoOpSheduler.class);
            }
        });

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
    @NotNull public ICredentialsProv creds() {
        ICredentialsProv mock = Mockito.mock(ICredentialsProv.class);

        when(mock.hasAccess(anyString())).thenReturn(true);
        when(mock.getUser(anyString())).thenReturn("mtcga");
        when(mock.getPassword(anyString())).thenReturn("123");

        return mock;
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
        Injector injector = Guice.createInjector(new TeamcityIgnitedModule(), new IgniteAndShedulerTestModule());

        injector.getInstance(RunHistCompactedDao.class).init();
        final IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final String srvId = "apache";
        final String btId = "RunAll";
        final String branch = ITeamcity.DEFAULT;

        final PrChainsProcessorTest tst = new PrChainsProcessorTest();
        tst.initBuildChain(c, btId, branch);

        final Map<Integer, FatBuildCompacted> buildsMap = tst.apacheBuilds();

        final RunHistSync histSync = injector.getInstance(RunHistSync.class);
        buildsMap.forEach((id, build) -> histSync.saveToHistoryLater(srvId, build));

        final ITeamcityIgnitedProvider inst = injector.getInstance(ITeamcityIgnitedProvider.class);
        final ITeamcityIgnited srv = inst.server(srvId, Mockito.mock(ICredentialsProv.class));
        final IRunHistory testRunHist = srv.getTestRunHist(new TestInBranch(PrChainsProcessorTest.TEST_FLAKY_IN_MASTER, branch));

        assertNotNull(testRunHist);
        assertEquals(0.5, testRunHist.getFailRate(), 0.1);

        final IRunHistory cache1Hist = srv.getSuiteRunHist(new SuiteInBranch(PrChainsProcessorTest.CACHE_1, branch));

        assertNotNull(cache1Hist);
        assertEquals(1.0, cache1Hist.getFailRate(), 0.1);
        assertEquals(0.18, cache1Hist.getCriticalFailRate(), 0.05);

        final IRunStat cache1HistAllBranch = srv.getSuiteRunStatAllBranches(PrChainsProcessorTest.CACHE_1);

        assertNotNull(cache1HistAllBranch);

        String printable = cache1HistAllBranch.getFailPercentPrintable();
        System.err.println(printable);
        // should be several builds in a separate branch
        assertEquals(0.5, cache1HistAllBranch.getFailRate(), 0.05);
    }


    @Test
    public void testHistoryBackgroundUpdateWorks() {
        Injector injector = Guice.createInjector(new TeamcityIgnitedModule(), new IgniteAndShedulerTestModule());

        injector.getInstance(RunHistCompactedDao.class).init();

        final String srvId = "apache";
        final String btId = "RunAll";
        final String branch = ITeamcity.DEFAULT;

        final ITeamcityIgnitedProvider inst = injector.getInstance(ITeamcityIgnitedProvider.class);
        final ITeamcityIgnited srv = inst.server(srvId, Mockito.mock(ICredentialsProv.class));

        FatBuildDao fatBuildDao = injector.getInstance(FatBuildDao.class);
        fatBuildDao.init();

        BuildRefDao buildRefDao = injector.getInstance(BuildRefDao.class);
        buildRefDao.init();

        final IStringCompactor c = injector.getInstance(IStringCompactor.class);


        final PrChainsProcessorTest tst = new PrChainsProcessorTest();
        tst.initBuildChain(c, btId, branch);

        final Map<Integer, FatBuildCompacted> buildsMap = tst.apacheBuilds();

        buildsMap.forEach((id, build) -> {
            int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
            fatBuildDao.putFatBuild(srvIdMaskHigh, id, build);
            buildRefDao.save(srvIdMaskHigh, new BuildRefCompacted(build));
        });

        final RunHistSync histSync = injector.getInstance(RunHistSync.class);
        histSync.invokeLaterFindMissingHistory(srvId);

        final IRunHistory testRunHist = srv.getTestRunHist(new TestInBranch(PrChainsProcessorTest.TEST_FLAKY_IN_MASTER, branch));

        assertNotNull(testRunHist);
        assertEquals(0.5, testRunHist.getFailRate(), 0.1);
    }

    @Test
    public void testQueuedBuildsRemoved() {
        TeamcityIgnitedModule module = new TeamcityIgnitedModule();
        module.overrideHttp(new ITeamcityHttpConnection() {
            @Override public InputStream sendGet(String basicAuthTok, String url) throws IOException {
                throw new FileNotFoundException(url);
            }
        });
        Injector injector = Guice.createInjector(module, new IgniteAndShedulerTestModule());

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

        String srvId = APACHE;
        int srvIdInt = ITeamcityIgnited.serverIdToInt(srvId);
        ITeamcityConn srvConn = injector.getInstance(ITcServerFactory.class).createServer(srvId);

        buildRefDao.saveChunk(srvIdInt, Lists.newArrayList(refQ, refR));

        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdInt);
        assertFalse(checkNotNull(running).isEmpty());

        System.out.println("Running builds (before sync): " + printRefs(c, running));

        ProactiveFatBuildSync buildSync = injector.getInstance(ProactiveFatBuildSync.class);
        buildSync.ensureActualizationRequested(srvId, srvConn);

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

        buildSync.ensureActualizationRequested(srvId, srvConn);

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
        return running2.stream().map(bref->bref.toBuildRef(c)).collect(Collectors.toList());
    }

    /**
     *
     */
    private static class IgniteAndShedulerTestModule extends AbstractModule {
        /** {@inheritDoc} */
        @Override protected void configure() {
            bind(Ignite.class).toInstance(ignite);
            bind(IScheduler.class).to(DirectExecNoWaitSheduler.class).in(new SingletonScope());
        }
    }
}
