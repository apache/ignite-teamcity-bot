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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.servlet.ServletContext;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.BuildStatisticsSummary;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Builds History: includes statistic for every build and merged failed unmuted tests in specified time interval.
 */
public class BuildsHistory {
    /** */
    private String srvId;

    /** */
    private String projectId;

    /** */
    private String buildTypeId;

    /** */
    private String branchName;

    /** */
    private Date sinceDateFilter;

    /** */
    private Date untilDateFilter;

    /** */
    private Map<String, Set<String>> mergedTestsBySuites = new ConcurrentHashMap<>();

    /** */
    private boolean skipTests;

    /** */
    public List<BuildStatisticsSummary> buildsStatistics = new ArrayList<>();

    /** */
    public String mergedTestsJson;

    /** */
    private static final Logger logger = LoggerFactory.getLogger(BuildsHistory.class);

    /** */
    public void initialize(ICredentialsProv prov, ServletContext context) {
        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        ITcHelper tcHelper = CtxListener.getTcHelper(context);

        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(srvId, prov);

        int[] finishedBuildsIds = teamcity.getBuildNumbersFromHistory(buildTypeId, branchName,
            sinceDateFilter, untilDateFilter);

        initStatistics(teamcity, finishedBuildsIds);

        if (!skipTests) {
            initFailedTests(teamcity, finishedBuildsIds);
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            mergedTestsJson = objectMapper.writeValueAsString(mergedTestsBySuites);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /** */
    private void initStatistics(IAnalyticsEnabledTeamcity teamcity, int[] buildIds) {
        List<Future<BuildStatisticsSummary>> buildStatiscsFutures = new ArrayList<>();

        for (int buildId : buildIds) {
            Future<BuildStatisticsSummary> buildFuture = CompletableFuture.supplyAsync(() -> {
                BuildStatisticsSummary buildsStatistic = new BuildStatisticsSummary(buildId);

                buildsStatistic.initialize(teamcity);

                return buildsStatistic;

            }, teamcity.getExecutor());

            buildStatiscsFutures.add(buildFuture);
        }

        buildStatiscsFutures.forEach(v -> {
            try {
                BuildStatisticsSummary buildsStatistic = v.get();

                if (buildsStatistic != null && !buildsStatistic.isFakeStub)
                    buildsStatistics.add(buildsStatistic);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof  UncheckedIOException)
                    logger.error(e.getStackTrace().toString());

                else
                    throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** */
    private Map<Integer, String> getConfigurations(ITeamcity teamcity, int buildId) {
        Map<Integer, String> configurations = new HashMap<>();

        FullQueryParams key = new FullQueryParams();

        key.setServerId(teamcity.serverId());
        key.setBuildId(buildId);

        teamcity.getConfigurations(key).getBuilds().forEach(buildRef -> {
            Integer id = buildRef.getId();

            String configurationName = buildRef.buildTypeId;

            if (id != null && configurationName != null)
                configurations.put(id, configurationName);
        });

        return configurations;
    }

    /** */
    private void initFailedTests(IAnalyticsEnabledTeamcity teamcity, int[] buildIds) {
        List<Future<Void>> buildProcessorFutures = new ArrayList<>();

        for (int buildId : buildIds) {
            Future<Void> buildFuture = CompletableFuture.supplyAsync(() -> {
                Map<Integer, String> configurations = getConfigurations(teamcity, buildId);

                Build build = teamcity.getBuild(teamcity.getBuildHrefById(buildId));

                TestOccurrences testOccurrences = teamcity.getFailedTests(build.testOccurrences.href,
                    build.testOccurrences.failed, BuildChainProcessor.normalizeBranch(build.branchName));

                for (TestOccurrence testOccurrence : testOccurrences.getTests()) {
                    String configurationName = configurations.get(testOccurrence.getBuildId());

                    if(configurationName == null)
                        continue;

                    Set<String> tests = mergedTestsBySuites.computeIfAbsent(configurationName,
                        k -> new HashSet<>());

                    if (!tests.add(testOccurrence.getName()))
                        continue;

                    FullQueryParams key = new FullQueryParams();

                    key.setServerId(srvId);
                    key.setProjectId(projectId);
                    key.setTestName(testOccurrence.getName());
                    key.setSuiteId(configurationName);

                    teamcity.getTestRef(key);
                }

                return null;
            }, teamcity.getExecutor());

            buildProcessorFutures.add(buildFuture);
        }

        buildProcessorFutures.forEach(v -> {
            try {
                v.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof  UncheckedIOException)
                    logger.error(e.getStackTrace().toString());

                else
                    throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** */
    public BuildsHistory(Builder builder) {
        this.skipTests = builder.skipTests;
        this.srvId = builder.srvId;
        this.buildTypeId = builder.buildTypeId;
        this.branchName = builder.branchName;
        this.sinceDateFilter = builder.sinceDate;
        this.untilDateFilter = builder.untilDate;
        this.projectId = builder.projectId;
    }

    /** */
    public static class Builder {
        /** */
        private boolean skipTests = false;

        /** */
        private String projectId = "IgniteTests24Java8";

        /** */
        private String srvId = "apache";

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

        /** */
        public Builder server(String srvId) {
            if (!isNullOrEmpty(srvId))
                this.srvId = srvId;

            return this;
        }

        /** */
        public Builder buildType(String buildType) {
            if (!isNullOrEmpty(buildType))
                this.buildTypeId = buildType;

            return this;
        }

        /** */
        public Builder project(String projectId) {
            if (!isNullOrEmpty(projectId))
                this.projectId = projectId;

            return this;
        }

        /** */
        public Builder branch(String branchName) {
            if (!isNullOrEmpty(branchName))
                this.branchName = branchName;

            return this;
        }

        /** */
        public Builder sinceDate(String sinceDate) throws ParseException {
            if (!isNullOrEmpty(sinceDate))
                this.sinceDate = dateFormat.parse(sinceDate);

            return this;
        }

        /** */
        public Builder untilDate(String untilDate) throws ParseException {
            if (!isNullOrEmpty(untilDate))
                this.untilDate = dateFormat.parse(untilDate);

            return this;
        }

        /** */
        public Builder skipTests() {
            this.skipTests = true;

            return this;
        }


        /** */
        public BuildsHistory build() {
            return new BuildsHistory(this);
        }
    }
}
