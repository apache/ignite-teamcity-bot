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
package org.apache.ignite.ci.tcbot.chain;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.pr.PrChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.tcservice.model.result.tests.TestRef;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link PrChainsProcessor} and blockers detection. Emulates builds using Mockito. Does not start an
 * Ignite node.
 */
public class PrChainsProcessorTest {
    public static final String SRV_ID = "apache";
    public static final String TEST_WITH_HISTORY_FAILING_IN_MASTER = "testWithHistoryFailingInMaster";
    public static final String TEST_WITH_HISTORY_PASSING_IN_MASTER = "testWithHistoryPassingInMaster";

    /** Test which is flaky in master, fail rate ~50%. */
    public static final String TEST_FLAKY_IN_MASTER = "testFlaky50";

    public static final String TEST_WITHOUT_HISTORY = "testWithoutHistory";
    public static final String TEST_WAS_FIXED_IN_MASTER = "testFailingButFixedInMaster";
    public static final int NUM_OF_TESTS_IN_MASTER = 10;
    public static final String CACHE_1 = "Cache1";

    /** Cache 9: Used to test flaky and non flaky tests detection. */
    public static final String CACHE_9 = "Cache9";

    /** Test rare failed with changes in build: should not be considered flaky in master and should became blocker. */
    public static final String TEST_RARE_FAILED_WITH_CHANGES = "testWithRareFailuresWithChanges";

    /** Test rare failed without any changes in build: should be considered flaky, and will not appear as blocker. */
    public static final String TEST_RARE_FAILED_WITHOUT_CHANGES = "testWithRareFailuresWithoutChanges";

    /** Build apache ignite: in some tests may contains compilation error, should became a blocker. */
    public static final String BUILD_APACHE_IGNITE = "Build";

    /** Cache 8: Used to test templates detection for PR branch. */
    public static final String CACHE_8 = "Cache8";

    /** Test, which was passing but became failed in branch. */
    public static final String TEST_BECAME_FAILED_IN_BRANCH = "testBecameFailedInBranch";

    /** Builds emulated storage. */
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();

    /**
     * Injector.
     */
    private Injector injector = Guice.createInjector(new MockBasedTcBotModule());

    /** */
    @Before
    public void initBuilds() {
        resetCaches();

        final TeamcityIgnitedProviderMock instance = (TeamcityIgnitedProviderMock)injector.getInstance(ITeamcityIgnitedProvider.class);
        instance.addServer(SRV_ID, apacheBuilds);
    }

    public void resetCaches() {
        BuildRefCompacted.resetCached();
        TestCompactedV2.resetCached();
        TestCompactedMult.resetCached();
    }

    //todo flaky test
    @Test
    public void testTestFailureWithoutStatReportedAsBlocker() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final String btId = "RunAll";
        final String branch = "ignite-9542";

        initBuildChainAndMasterHistory(c, btId, branch);

        PrChainsProcessor prcp = injector.getInstance(PrChainsProcessor.class);
        final List<ShortSuiteUi> blockers = prcp.getBlockersSuitesStatuses(btId, branch, SRV_ID, mock(ITcBotUserCreds.class), SyncMode.RELOAD_QUEUED, null);

        System.out.println(blockers);
        assertNotNull(blockers);
        assertFalse(blockers.isEmpty());

        assertTrue(containsTestFailure(blockers, TEST_WITHOUT_HISTORY));

        assertTrue(blockers.stream().anyMatch(s -> BUILD_APACHE_IGNITE.equals(s.name)));
        assertTrue(blockers.stream().anyMatch(s -> "CancelledBuild".equals(s.name)));

        assertTrue(containsTestFailure(blockers, TEST_WITH_HISTORY_PASSING_IN_MASTER));

        assertFalse(containsTestFailure(blockers, TEST_WITH_HISTORY_FAILING_IN_MASTER));
        assertFalse(containsTestFailure(blockers, TEST_FLAKY_IN_MASTER));

        Optional<? extends ShortTestFailureUi> testOpt = findBlockerTestFailure(blockers, TEST_WITH_HISTORY_PASSING_IN_MASTER);
        assertTrue(testOpt.isPresent());

        assertTrue(containsTestFailure(blockers, TEST_WAS_FIXED_IN_MASTER));
        assertFalse(containsTestFailure(blockers, TEST_WITH_HISTORY_FAILING_IN_MASTER));
        // otherwise this non-blocker will not be filtered out

        assertTrue(containsTestFailure(blockers, TEST_WITH_HISTORY_PASSING_IN_MASTER));
    }

    public boolean containsTestFailure(List<ShortSuiteUi> blockers, String name) {
        return blockers.stream().anyMatch(containsTestFail(name));
    }

    /**
     * Tests flaky detection mechanism. If master failures contained changes this test is non flaky. If test changes its
     * status wihtout code modificaitons this test should be marged as flaky.
     */
    @Test
    public void testFlakyDetector() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final String btId = "RunAll";
        final String branch = "ignite-10056";

        initChainForFlakyTest(c, btId, branch);

        initHistory(c);

        PrChainsProcessor prcp = injector.getInstance(PrChainsProcessor.class);
        final List<ShortSuiteUi> blockers = prcp.getBlockersSuitesStatuses(btId, branch, SRV_ID, mock(ITcBotUserCreds.class), SyncMode.RELOAD_QUEUED, null);

        System.out.println(blockers);

        Optional<? extends ShortTestFailureUi> rareNotFlaky = findBlockerTestFailure(blockers, TEST_RARE_FAILED_WITH_CHANGES);
        assertTrue(rareNotFlaky.isPresent());

        assertFalse(findBlockerTestFailure(blockers, TEST_RARE_FAILED_WITHOUT_CHANGES).isPresent());
    }

    public Optional<? extends ShortTestFailureUi> findBlockerTestFailure(List<ShortSuiteUi> blockers, String name) {
        Optional<ShortSuiteUi> suiteOpt = blockers.stream().filter(containsTestFail(name)).findAny();

        return suiteOpt.flatMap(suite -> suite.testFailures().stream().filter(tf -> name.equals(tf.name)).findAny());
    }

    /**
     * @param name Test failure Name to find.
     */
    @NotNull
    private Predicate<ShortSuiteUi> containsTestFail(String name) {
        return s -> s.testFailures().stream().anyMatch(testFailure -> {
            return name.equals(testFailure.name);
        });
    }

    public void initBuildChainAndMasterHistory(IStringCompactor c, String btId, String branch) {
        initBuildChain(c, btId, branch);

        initHistory(c);
    }

    /**
     * Initializes master test runs to be used as refenence.
     *
     * @param c Compactor.
     */
    public Map<Integer, FatBuildCompacted> initHistory(IStringCompactor c) {
        for (int i = 0; i < NUM_OF_TESTS_IN_MASTER; i++) {
            FatBuildCompacted cache1InMaster = createFailedBuild(c, CACHE_1,
                ITeamcity.DEFAULT, 500 + i, (i * 1000))
                .addTests(c, Lists.newArrayList(
                    createFailedTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                    createPassingTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                    createTest(50L, TEST_FLAKY_IN_MASTER, i % 2 == 0),
                    createPassingTest(400L, TEST_WAS_FIXED_IN_MASTER)), null);

            if (i % 7 == 1) {
                ProblemOccurrence timeout = new ProblemOccurrence();
                timeout.setType(ProblemOccurrence.TC_EXECUTION_TIMEOUT);
                cache1InMaster.addProblems(c, Collections.singletonList(timeout));
            }

            if (i == 0) {
                cache1InMaster.changes(new int[]{123}); // emulating change
            }

            addBuildsToEmulatedStor(cache1InMaster);
        }

        long ageMs = TimeUnit.DAYS.toMillis(TcBotConst.HISTORY_MAX_DAYS);

        for (int i = 0; i < 134; i++) {
            addBuildsToEmulatedStor(createFailedBuild(c, CACHE_1,
                ITeamcity.DEFAULT, i, ageMs + (i * 10000))
                .addTests(c, Lists.newArrayList(
                    createFailedTest(400L, TEST_WAS_FIXED_IN_MASTER)), null));
        }

        for (int i = 0; i < 10; i++) {
            final FatBuildCompacted successfull =
                createFatBuild(c, CACHE_1, "some-exotic-branch", i + 7777, 100020, true)
                    .addTests(c,
                        Lists.newArrayList(
                            createPassingTest(1L, TEST_WITHOUT_HISTORY),
                            createPassingTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                            createPassingTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                            createPassingTest(50L, TEST_FLAKY_IN_MASTER),
                            createPassingTest(400L, TEST_WAS_FIXED_IN_MASTER)), null);

            addBuildsToEmulatedStor(successfull);
        }

        for (int i = 0; i < 100; i++) {
            boolean failNoChanges = i == 77;
            boolean failWithChanges = i == 55;

            boolean passed = !failNoChanges && !failWithChanges;

            FatBuildCompacted fatBuild = createFatBuild(c, CACHE_9, ITeamcity.DEFAULT, i + 9999, 1340020, passed)
                .addTests(c,
                    Lists.newArrayList(
                        createTest(1L, TEST_RARE_FAILED_WITHOUT_CHANGES, !failNoChanges),
                        createTest(2L, TEST_RARE_FAILED_WITH_CHANGES, !failWithChanges)), null);

            if (failWithChanges || i == 56) // addBuild change to test status change after failure.
                fatBuild.changes(new int[] {1000000 + i, 1000020 + i});

            addBuildsToEmulatedStor(fatBuild);
        }

        return apacheBuilds();
    }

    /**
     * Initializes
     *
     * @param c Compatcor.
     * @param btId Build type.
     * @param branch Branch tested.
     */
    public void initBuildChain(IStringCompactor c, String btId, String branch) {
        final FatBuildCompacted buildBuild = createFailedBuild(c, BUILD_APACHE_IGNITE, branch, 1002, 100020);
        final ProblemOccurrence compile = new ProblemOccurrence();
        compile.setType(ProblemOccurrence.TC_COMPILATION_ERROR);
        buildBuild.addProblems(c, Collections.singletonList(compile));

        final FatBuildCompacted cache1 =
            createFailedBuild(c, CACHE_1, branch, 1001, 100020)
                .addTests(c,
                    Lists.newArrayList(
                        createFailedTest(1L, TEST_WITHOUT_HISTORY),
                        createFailedTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                        createFailedTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                        createFailedTest(50L, TEST_FLAKY_IN_MASTER),
                        createFailedTest(400L, TEST_WAS_FIXED_IN_MASTER)), null);

        cache1.snapshotDependencies(new int[] {buildBuild.id()});

        final Build build = createJaxbBuild("CancelledBuild", branch, 1003, 100020, true);

        build.status = BuildRef.STATUS_UNKNOWN;
        build.state = BuildRef.STATE_FINISHED;

        final FatBuildCompacted cancelledBuild = new FatBuildCompacted(c, build);

        cancelledBuild.snapshotDependencies(new int[] {buildBuild.id()});

        final int id = 1000;

        FatBuildCompacted runAll = createFailedBuild(c, btId, branch, id, 100000)
            .snapshotDependencies(new int[] {cache1.id(), cancelledBuild.id()});

        addBuildsToEmulatedStor(buildBuild, cancelledBuild, cache1, runAll);
    }

    /**
     * @param c Compactor.
     * @param btId Build Type id.
     * @param branch Branch.
     */
    public void initChainForFlakyTest(IStringCompactor c, String btId, String branch) {
        final FatBuildCompacted buildBuild = createFailedBuild(c, BUILD_APACHE_IGNITE, branch, 1002, 100020);

        final FatBuildCompacted cache9 = createFailedBuild(c, CACHE_9, branch, 9001, 100090)
            .addTests(c,
                Lists.newArrayList(
                    createFailedTest(1L, TEST_RARE_FAILED_WITH_CHANGES),
                    createFailedTest(2L, TEST_RARE_FAILED_WITHOUT_CHANGES)), null);

        cache9.snapshotDependencies(new int[] {buildBuild.id()});

        final FatBuildCompacted chain =
            createFailedBuild(c, btId, branch, 1000, 100000)
                .snapshotDependencies(new int[] {cache9.id()});

        addBuildsToEmulatedStor(buildBuild, cache9, chain);
    }

    /**
     * Adds builds into emulated storage.
     *
     * @param builds Builds.
     */
    private void addBuildsToEmulatedStor(FatBuildCompacted... builds) {
        for (FatBuildCompacted build : builds) {
            final FatBuildCompacted oldB = apacheBuilds.put(build.id(), build);

            Preconditions.checkState(oldB == null);
        }
    }

    @NotNull
    private TestOccurrenceFull createFailedTest(long id, String name) {
        return createTest(id, name, false);
    }

    @NotNull
    private TestOccurrenceFull createPassingTest(long id, String name) {
        return createTest(id, name, true);
    }

    @NotNull public static TestOccurrenceFull createTest(long id, String name, boolean passed) {
        TestOccurrenceFull tf = new TestOccurrenceFull();

        tf.test = new TestRef();

        tf.test.id = String.valueOf(id);
        tf.name = name;
        tf.status = passed ? TestOccurrence.STATUS_SUCCESS : TestOccurrence.STATUS_FAILURE;
        return tf;
    }

    @NotNull
    public FatBuildCompacted createFailedBuild(IStringCompactor c, String btId, String branch, int id, long ageMs) {
        return createFatBuild(c, btId, branch, id, ageMs, false);
    }

    @NotNull public static FatBuildCompacted createFatBuild(IStringCompactor c, String btId, String branch, int id,
        long ageMs,
        boolean passed) {
        final Build build = createJaxbBuild(btId, branch, id, ageMs, passed);

        return new FatBuildCompacted(c, build);
    }

    @NotNull
    private static Build createJaxbBuild(String btId, String branch, int id, long ageMs, boolean passed) {
        final Build build = new Build();
        build.buildTypeId = btId;
        final BuildType type = new BuildType();
        type.setId(btId);
        type.setName(btId);
        build.setBuildType(type);
        build.setId(id);
        build.setStartDateTs(System.currentTimeMillis() - ageMs);
        build.setBranchName(branch);
        build.state = Build.STATE_FINISHED;
        build.status = passed ? Build.STATUS_SUCCESS : BuildRef.STATUS_FAILURE;

        return build;
    }

    public Map<Integer, FatBuildCompacted> apacheBuilds() {
        return apacheBuilds;
    }

    /**
     * @param c Compactor.
     * @param btId Chain Build type id.
     * @param branch Branch.
     */
    public void initChainForTemplateDetection(IStringCompactor c, String btId, String branch) {
        int firstFailedBuild = 6;

        for (int i = 0; i < 10; i++) {
            final FatBuildCompacted buildBuild = createFailedBuild(c, BUILD_APACHE_IGNITE, branch, 1332 + i, 100020);

            final FatBuildCompacted cache8 =
                createFailedBuild(c, CACHE_8, branch, 9331 + i, 100090)
                    .addTests(c,
                        Lists.newArrayList(
                            createTest(1L, TEST_BECAME_FAILED_IN_BRANCH, i < firstFailedBuild)), null)
                    .snapshotDependencies(new int[] {buildBuild.id()});

            if (i == firstFailedBuild)
                cache8.changes(new int[] {i}); //change which failed this test

            final FatBuildCompacted chain =
                createFailedBuild(c, btId, branch, 1220 + i, 100000)
                    .snapshotDependencies(new int[] {cache8.id()});

            addBuildsToEmulatedStor(buildBuild, cache8, chain);
        }
    }

    @Test
    public void testTemplateDetectionInBranch() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        final String btId = "RunAll";
        final String branch = "ignite-9542";

        initChainForTemplateDetection(c, btId, branch);

        initHistory(c);

        PrChainsProcessor prcp = injector.getInstance(PrChainsProcessor.class);

        final List<ShortSuiteUi> blockers = prcp.getBlockersSuitesStatuses(btId, branch, SRV_ID, mock(ITcBotUserCreds.class), SyncMode.RELOAD_QUEUED, null);

        System.out.println(blockers);

        Optional<? extends ShortTestFailureUi> testBecameFailed = findBlockerTestFailure(blockers, TEST_BECAME_FAILED_IN_BRANCH);
        assertTrue(testBecameFailed.isPresent());
    }
}
