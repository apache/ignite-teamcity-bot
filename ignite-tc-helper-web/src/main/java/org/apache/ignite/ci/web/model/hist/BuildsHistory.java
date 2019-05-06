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

package org.apache.ignite.ci.web.model.hist;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.UncheckedIOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.trends.MasterTrendsService;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.BuildStatisticsSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Presents statistic for all valid builds and merged failed unmuted tests in specified time interval. Statistics and
 * tests are stored in {@link #buildsStatistics} and {@link #mergedTestsBySuites} properties respectively. Builder
 * pattern is used for instance creation. Default values: <br>skipTests = false,<br>projectId="IgniteTests24Java8",
 * <br>srvCode="apache", <br>buildTypeId="IgniteTests24Java8_RunAll",
 * <br>branchName="refs/heads/master".
 */
public class BuildsHistory {
    /** */
    private String srvCode;

    /** */
    public String projectId;

    public String tcHost;

    /** */
    private String buildTypeId;

    /** */
    private String branchName;

    /** */
    private Date sinceDateFilter;

    /** */
    private Date untilDateFilter;

    /** Suite name -> map of test name -> [test Name ID: String to avoid JS overflow, fail rate: float] */
    public Map<String, Map<String, List<Object>>> mergedTestsBySuites = new ConcurrentHashMap<>();

    /** */
    private boolean skipTests;

    /** Build statistics for all valid builds in specified time interval. */
    public List<BuildStatisticsSummary> buildsStatistics = new ArrayList<>();

    /** */
    private static final Logger logger = LoggerFactory.getLogger(BuildsHistory.class);

    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    @Inject private MasterTrendsService masterTrendsService;

    @Inject private IStringCompactor compactor;

    /**
     * Initialize {@link #mergedTestsBySuites} and {@link #buildsStatistics} properties using builds which satisfy
     * properties setted by Builder.
     *
     * @param prov Credentials.
     */
    public void initialize(ICredentialsProv prov) {
        ITeamcityIgnited ignitedTeamcity = tcIgnitedProv.server(srvCode, prov);

        tcHost = ignitedTeamcity.host();

        List<Integer> finishedBuildsIds = ignitedTeamcity
            .getFinishedBuildsCompacted(buildTypeId, branchName, sinceDateFilter, untilDateFilter)
            .stream().mapToInt(BuildRefCompacted::id).boxed()
            .collect(Collectors.toList());

        Map<Integer, Boolean> buildIdsWithConditions = finishedBuildsIds.stream()
            .collect(Collectors.toMap(v -> v, ignitedTeamcity::buildIsValid, (e1, e2) -> e1, LinkedHashMap::new));

        initStatistics(ignitedTeamcity, buildIdsWithConditions);

        List<Integer> validBuilds = buildIdsWithConditions.keySet()
            .stream()
            .filter(buildIdsWithConditions::get)
            .collect(Collectors.toList());

        if (!skipTests)
            initFailedTests(validBuilds, buildIdsWithConditions);

        if (MasterTrendsService.DEBUG)
            System.out.println("Preparing response");
    }

    /**
     * Initialize {@link #buildsStatistics} property with list of {@link BuildStatisticsSummary} produced for each valid
     * build.
     *
     * @param ignited {@link ITeamcityIgnited} instance.
     * @param buildIdsWithConditions Build ID -> build validation flag.
     */
    private void initStatistics(ITeamcityIgnited ignited,
        Map<Integer, Boolean> buildIdsWithConditions) {
        List<Future<BuildStatisticsSummary>> buildStaticsFutures = new ArrayList<>();

        for (int buildId : buildIdsWithConditions.keySet()) {
            Future<BuildStatisticsSummary> buildFut = CompletableFuture.supplyAsync(() -> {
                BuildStatisticsSummary buildsStatistic = masterTrendsService.getBuildSummary(ignited, buildId);
                buildsStatistic.isValid = buildIdsWithConditions.get(buildId);

                return buildsStatistic;
            });

            buildStaticsFutures.add(buildFut);
        }

        if (MasterTrendsService.DEBUG)
            System.out.println("Waiting for stat to collect");

        buildStaticsFutures.forEach(fut -> {
            try {
                BuildStatisticsSummary buildsStatistic = fut.get();

                if (buildsStatistic != null && !buildsStatistic.isFakeStub)
                    buildsStatistics.add(buildsStatistic);
            }
            catch (ExecutionException e) {
                if (e.getCause() instanceof UncheckedIOException)
                    logger.error(Arrays.toString(e.getStackTrace()));

                else
                    throw new RuntimeException(e);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Initialize {@link #mergedTestsBySuites} property by unique failed tests which occured in specified date
     * interval.
     *
     * @param buildIds list of valid builds.
     * @param buildIdsWithConditions Build ID -> build validation flag.
     */
    private void initFailedTests(List<Integer> buildIds,
        Map<Integer, Boolean> buildIdsWithConditions) {

        for (BuildStatisticsSummary buildStat : buildsStatistics) {
            Boolean valid = buildIdsWithConditions.get(buildStat.buildId);
            if (!Boolean.TRUE.equals(valid))
                continue;

            buildStat.failedTests().forEach((btId, map) -> {
                String configurationName = compactor.getStringFromId(btId);
                Map<String, List<Object>> tests = mergedTestsBySuites.computeIfAbsent(configurationName,
                    k -> new HashMap<>());

                map.forEach((tn, pair) -> {
                    String testName = compactor.getStringFromId(tn);
                    Integer cnt = pair.get2();
                    float i = cnt != null ? cnt : 1F;

                    float addForFailRate = i / buildIds.size();

                    tests.merge(testName, Lists.newArrayList(Long.toString(pair.get1()), addForFailRate),
                        (a, b) -> {
                            if (a == null)
                                return b;
                            return Lists.newArrayList(a.get(0), (Float)a.get(1) + (Float)b.get(1));
                        });
                });
            });
        }
    }

    private BuildsHistory withParameters(Builder builder) {
        this.skipTests = builder.skipTests;
        this.srvCode = builder.srvCode;
        this.buildTypeId = builder.buildTypeId;
        this.branchName = builder.branchName;
        this.sinceDateFilter = builder.sinceDate;
        this.untilDateFilter = builder.untilDate;
        this.projectId = builder.projectId;
        return this;
    }

    /** */
    public static class Builder {
        /** */
        private boolean skipTests = false;

        /** */
        private String projectId = "IgniteTests24Java8";

        /** */
        private String srvCode;

        /** */
        private String buildTypeId = "IgniteTests24Java8_RunAll";

        /** */
        private String branchName = "refs/heads/master";

        /** */
        private Date sinceDate = null;

        /** */
        private Date untilDate = null;

        /** */
        private DateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");

        public Builder(ITcBotConfig cfg) {
            srvCode = cfg.primaryServerCode();
        }

        /**
         * @param srvId server name.
         */
        public Builder server(String srvId) {
            if (!isNullOrEmpty(srvId))
                this.srvCode = srvId;

            return this;
        }

        /**
         * @param buildType TC suite(buildType) name.
         */
        public Builder buildType(String buildType) {
            if (!isNullOrEmpty(buildType))
                this.buildTypeId = buildType;

            return this;
        }

        /**
         * @param projectId TC project name.
         */
        public Builder project(String projectId) {
            if (!isNullOrEmpty(projectId))
                this.projectId = projectId;

            return this;
        }

        /**
         * @param branchName TC branch name.
         */
        public Builder branch(String branchName) {
            if (!isNullOrEmpty(branchName))
                this.branchName = branchName;

            return this;
        }

        /**
         * @param sinceDate left border of date interval form which builds will be presented.
         */
        public Builder sinceDate(String sinceDate) throws ParseException {
            if (!isNullOrEmpty(sinceDate))
                this.sinceDate = dateFormat.parse(sinceDate);

            return this;
        }

        /**
         * @param untilDate right border of date interval form which builds will be presented.
         */
        public Builder untilDate(String untilDate) throws ParseException {
            if (!isNullOrEmpty(untilDate))
                this.untilDate = dateFormat.parse(untilDate);

            return this;
        }

        /** Set flag to skip collection of failed tests info. */
        public Builder skipTests() {
            this.skipTests = true;

            return this;
        }

        /**
         * @param injector Guice instance for dependency injection.
         */
        public BuildsHistory build(Injector injector) {
            final BuildsHistory instance = injector.getInstance(BuildsHistory.class);

            return instance.withParameters(this);
        }
    }
}
