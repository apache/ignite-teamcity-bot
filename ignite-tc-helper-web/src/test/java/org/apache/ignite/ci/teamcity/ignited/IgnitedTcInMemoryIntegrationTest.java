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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBException;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.scheduler.DirectExecNoWaitSheduler;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.di.scheduler.NoOpSheduler;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.pure.BuildHistoryEmulator;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
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

        String buildTypeId = "IgniteTests24Java8_RunAll";
        String branchName = "<default>";
        List<BuildRef> hist = srv.getBuildHistory(buildTypeId, branchName);
        //todo mult branches including pull/4926/head

        assertTrue(!hist.isEmpty());

        for (BuildRef h : hist) {
            System.out.println(h);

            assertEquals(buildTypeId, h.suiteId());

            assertEquals("refs/heads/master", h.branchName());
        }

        ignite.cache(STRINGS_CACHE).forEach(
            (e) -> {
                System.out.println(e.getValue());
            }
        );
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

        TeamcityIgnitedImpl teamcityIgnited = (TeamcityIgnitedImpl)srv;
        teamcityIgnited.fullReindex();
        String buildTypeId = "IgniteTests24Java8_RunAll";
        String branchName = "<default>";
        List<String> statues = srv.getBuildHistory(buildTypeId, branchName).stream().map(BuildRef::state).distinct().collect(Collectors.toList());
        System.out.println("Before " + statues);

        for (int i = queuedBuildIdx; i < tcBuilds.size(); i++)
            tcBuilds.get(i).state = BuildRef.STATE_FINISHED;

        teamcityIgnited.actualizeRecentBuildRefs();


        List<BuildRef> hist = srv.getBuildHistory(buildTypeId, branchName);

        assertTrue(!hist.isEmpty());

        for (BuildRef h : hist) {
            assertEquals(buildTypeId, h.suiteId());

            assertEquals("refs/heads/master", h.branchName());

            assertTrue("Build " + h + " is expected to be finished" , h.isFinished());
        }

        statues = hist.stream().map(BuildRef::state).distinct().collect(Collectors.toList());

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
                bind(IStringCompactor.class).to(IgniteStringCompactor.class);
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
        assertTrue(duration>10000L);

        int[] ch = buildCompacted.changes();

        assertEquals(6, ch.length);
    }

    public void saveTmpFile(Object obj, String name) throws IOException, JAXBException {
        ensureDirExist(new File(name).getParentFile());

        try (FileWriter writer = new FileWriter(name)) {
            writer.write(XmlUtil.save(obj));
        }
    }

    public <E> E jaxbTestXml(String ref, Class<E> cls) throws IOException, JAXBException {
        E refBuild;
        try(InputStream stream = getClass().getResourceAsStream(ref)) {
            refBuild = XmlUtil.load(cls, new InputStreamReader(stream));
        }
        return refBuild;
    }

}
