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
package org.apache.ignite.tcignited.history;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.InvocationData;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.SuiteHistory;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class HistoryCollector {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(HistoryCollector.class);

    /** History DAO. */
    @Inject private SuiteInvocationHistoryDao histDao;

    /** Fat build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Branch equivalence. */
    @Inject private BranchEquivalence branchEquivalence;

    /** Run history DAO. */
    @Inject private RunHistCompactedDao runHistCompactedDao;

    /**
     * Non persistence cache for all suite RunHistory for particular branch. RunHistKey(ServerId||BranchId||suiteId)->
     * Build reference
     */
    private final com.google.common.cache.Cache<RunHistKey, SuiteHistory> runHistInMemCache
        = CacheBuilder.newBuilder()
        .maximumSize(8000)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();

    /** Biggest build ID, which out of history scope (MAX days + 2). */
    private final ConcurrentMap<Integer, AtomicInteger> biggestBuildIdOutOfHistoryScope = new ConcurrentHashMap<>();

    /**
     * @param srvIdMaskHigh Server id mask to be placed at high bits in the key.
     * @param testName Test name.
     * @param buildTypeId Suite (Build type) id.
     * @param normalizedBaseBranch Branch name.
     */
    public IRunHistory getTestRunHist(int srvIdMaskHigh, int testName, int buildTypeId,
        int normalizedBaseBranch) {

        SuiteHistory hist = getSuiteHist(srvIdMaskHigh, buildTypeId, normalizedBaseBranch);

        return hist.getTestRunHist(testName);
    }

    @AutoProfiling
    protected SuiteHistory getSuiteHist(int srvIdMaskHigh, int buildTypeId, int normalizedBaseBranch) {
        RunHistKey runHistKey = new RunHistKey(srvIdMaskHigh, buildTypeId, normalizedBaseBranch);

        SuiteHistory hist;
        try {
            hist = runHistInMemCache.get(runHistKey,
                () -> loadSuiteHistory(srvIdMaskHigh, buildTypeId, normalizedBaseBranch));
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        return hist;
    }

    /**
     *  Latest actual Build ids supplier. This supplier should handle all equivalent branches in
     *     it.
     * @param srvId
     * @param buildTypeId
     * @param normalizedBaseBranch
     * @param knownBuilds Known builds, which already present in run history.
     */
    @AutoProfiling
    protected Set<Integer> determineLatestBuilds(
        int srvId, int buildTypeId, int normalizedBaseBranch, Set<Integer> knownBuilds) {
        String btId = compactor.getStringFromId(buildTypeId);
        String branchId = compactor.getStringFromId(normalizedBaseBranch);
        List<BuildRefCompacted> bRefsList = buildRefDao.getAllBuildsCompacted(srvId, btId,
            branchEquivalence.branchForQuery(branchId));

        long curTs = System.currentTimeMillis();
        Set<Integer> buildIds = bRefsList.stream()
            .filter(b -> {
                AtomicInteger biggestIdOutOfScope = biggestBuildIdOutOfHistoryScope.get(srvId);
                int outOfScopeBuildId = biggestIdOutOfScope == null ? -1 : biggestIdOutOfScope.get();
                return b.id() > outOfScopeBuildId;
            })
            .filter(this::applicableForHistory)
            .map(BuildRefCompacted::id)
            .filter(bId -> !knownBuilds.contains(bId)).collect(Collectors.toSet());

        System.out.println("***** Loading build start time history for suite "
            + compactor.getStringFromId(buildTypeId)
            + " branch " + compactor.getStringFromId(normalizedBaseBranch) + ": " + buildIds.size() + " builds" );

        Map<Integer, Long> buildStartTimes = getStartTimeFromSpecialCache(srvId, buildIds);

        Set<Integer> notFoundKeys = new HashSet<>(buildIds);
        notFoundKeys.removeAll(buildStartTimes.keySet());

        if (!notFoundKeys.isEmpty()) {
            Map<Integer, Long> buildStartTimeFromFatBuild = getStartTimeFromFatBuild(srvId, notFoundKeys);

            buildStartTimes.putAll(buildStartTimeFromFatBuild);

            runHistCompactedDao.setBuildsStartTime(srvId, buildStartTimeFromFatBuild);
        }

        Set<Integer> buildInScope = buildIds.stream().filter(
            bId -> {
                Long startTime = buildStartTimes.get(bId);
                if (startTime == null)
                    return false;

                long ageInDays = Duration.ofMillis(curTs - startTime).toDays();

                if (ageInDays > InvocationData.MAX_DAYS + 2) {
                    AtomicInteger integer = biggestBuildIdOutOfHistoryScope.computeIfAbsent(srvId,
                        s -> {
                            AtomicInteger atomicInteger = new AtomicInteger();
                            atomicInteger.set(-1);
                            return atomicInteger;
                        });

                    int newBorder = integer.accumulateAndGet(bId, Math::max);

                    if (newBorder == bId)
                        logger.info("History Collector: New border for server was set " + bId);
                }

                return ageInDays < InvocationData.MAX_DAYS;
            }
        ).collect(Collectors.toSet());

        System.err.println("*** Build " + btId + " branch " + branchId + " builds in scope " +
            buildInScope.size() + " from " + bRefsList.size());

        return buildInScope;
    }

    @AutoProfiling
    protected Map<Integer, Long> getStartTimeFromSpecialCache(int srvId, Set<Integer> buildIds) {
        return runHistCompactedDao.getBuildsStartTime(srvId, buildIds);
    }

    @AutoProfiling
    protected Map<Integer, Long> getStartTimeFromFatBuild(int srvId, Set<Integer> buildIds) {
        return fatBuildDao.getBuildStartTime(srvId, buildIds);
    }

    /**
     * @param ref Build Reference or fat build.
     */
    private boolean applicableForHistory(BuildRefCompacted ref) {
        return !ref.isFakeStub() && !ref.isCancelled(compactor) && ref.isFinished(compactor);
    }

    @AutoProfiling
    protected SuiteHistory loadSuiteHistory(int srvId,
        int buildTypeId,
        int normalizedBaseBranch) {
        Map<Integer, SuiteInvocation> suiteRunHist = histDao.getSuiteRunHist(srvId, buildTypeId, normalizedBaseBranch);

        System.out.println("***** Found history for suite "
            + compactor.getStringFromId(buildTypeId)
            + " branch " + compactor.getStringFromId(normalizedBaseBranch) + ": " + suiteRunHist.size() );

        Set<Integer> buildIds = determineLatestBuilds(srvId, buildTypeId, normalizedBaseBranch, suiteRunHist.keySet());

        HashSet<Integer> missedBuildsIds = new HashSet<>(buildIds);

        missedBuildsIds.removeAll(suiteRunHist.keySet());

        if (!missedBuildsIds.isEmpty()) {
            Map<Integer, SuiteInvocation> addl = addSuiteInvocationsToHistory(srvId, missedBuildsIds, normalizedBaseBranch);

            suiteRunHist.putAll(addl);

            /*
            Map<Integer, SuiteInvocation> reloaded = histDao.getSuiteRunHist(srvId, buildTypeId, normalizedBaseBranch);

            addl.keySet().forEach((k) -> {
                Preconditions.checkState( reloaded.containsKey(k));
            });
            */
        }

        SuiteHistory sumary = new SuiteHistory();

        suiteRunHist.forEach((buildId, suiteInv) -> sumary.addSuiteInvocation(suiteInv));

        if (logger.isDebugEnabled()) {
            logger.debug("***** History for suite "
                + compactor.getStringFromId(buildTypeId)
                + " branch" + compactor.getStringFromId(normalizedBaseBranch) + " requires " +
                sumary.size(igniteProvider.get()) + " bytes");
        }

        return sumary;
    }

    /**
     * @param srvId Server id.
     * @param b Build ref to invalidate.
     */
    public void invalidateHistoryInMem(int srvId, BuildRefCompacted b) {
        RunHistKey inv = new RunHistKey(srvId, b.buildTypeId(), b.branchName());

        runHistInMemCache.invalidate(inv);
    }


    @AutoProfiling
    protected Map<Integer, SuiteInvocation> addSuiteInvocationsToHistory(int srvId,
        HashSet<Integer> missedBuildsIds, int normalizedBaseBranch) {
        Map<Integer, SuiteInvocation> suiteRunHist = new HashMap<>();
        int successStatusStrId = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);

        System.err.println(Thread.currentThread().getName() + ": GET ALL: " + missedBuildsIds.size());

        Iterables.partition(missedBuildsIds, 32 * 10).forEach(
            chunk -> {
                fatBuildDao.getAllFatBuilds(srvId, chunk).forEach((buildCacheKey, fatBuildCompacted) -> {
                    if (!applicableForHistory(fatBuildCompacted))
                        return;

                    BiPredicate<Integer, Integer> paramsFilter = (k, v) -> false;

                    SuiteInvocation sinv = new SuiteInvocation(srvId, normalizedBaseBranch, fatBuildCompacted, compactor, paramsFilter);

                    Stream<TestCompacted> tests = fatBuildCompacted.getAllTests();
                    tests.forEach(
                        testCompacted -> {
                            Invocation invocation = testCompacted.toInvocation(fatBuildCompacted, paramsFilter, successStatusStrId);

                            sinv.addTest(testCompacted.testName(), invocation);
                        }
                    );

                    suiteRunHist.put(fatBuildCompacted.id(), sinv);
                });
            }
        );


        System.err.println("***** + Adding to persisted history   "
            + " branch " + compactor.getStringFromId(normalizedBaseBranch) + ": added " +
            suiteRunHist.size() + " invocations from " + missedBuildsIds.size() + " builds checked");

        histDao.putAll(srvId, suiteRunHist);

        return suiteRunHist;
    }

    /**
     * @param srvId Server id.
     * @param buildTypeId Build type id.
     * @param normalizedBaseBranch Normalized base branch.
     */
    public ISuiteRunHistory getSuiteRunHist(int srvId, int buildTypeId,  int  normalizedBaseBranch) {
        return getSuiteHist(srvId, buildTypeId, normalizedBaseBranch);
    }

}
