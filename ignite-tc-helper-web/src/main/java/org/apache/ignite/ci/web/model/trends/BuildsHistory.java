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

package org.apache.ignite.ci.web.model.trends;

import com.google.common.collect.Lists;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Presents statistic for all valid builds and merged failed unmuted tests in specified time interval. Statistics and
 * tests are stored in {@link #buildsStatistics} and {@link #mergedTestsBySuites} properties respectively. Builder
 * pattern is used for instance creation. Default values: <br>skipTests = false,<br>projectId="IgniteTests24Java8",
 * <br>srvCode="apache", <br>buildTypeId="IgniteTests24Java8_RunAll",
 * <br>branchName="refs/heads/master".
 */
@SuppressWarnings("PublicField") public class BuildsHistory {
    /** */
    public String projectId;

    /** Normalized TC Host address, ends with '/'. */
    public String tcHost;

    /** */
    public String buildTypeId;

    /** */
    public String branchName;

    /** */
    public Date sinceDateFilter;

    /** */
    public Date untilDateFilter;

    /** Suite name -> map of test name -> [test Name ID: String to avoid JS overflow, fail rate: float] */
    public Map<String, Map<String, List<Object>>> mergedTestsBySuites = new ConcurrentHashMap<>();

    /** Build statistics for all valid builds in specified time interval. */
    public List<BuildStatisticsSummary> buildsStatistics = new ArrayList<>();

    /**
     * Initialize {@link #mergedTestsBySuites} property by unique failed tests which occured in specified date
     * interval.
     *
     * @param buildIds list of valid builds.
     * @param buildIdsWithConditions Build ID -> build validation flag.
     * @param compactor Compactor
     */
    public void initFailedTests(List<Integer> buildIds,
        Map<Integer, Boolean> buildIdsWithConditions, IStringCompactor compactor) {

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

    public BuildsHistory withParameters(Builder builder) {
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
        private String projectId = "IgniteTests24Java8";

        /** */
        private String buildTypeId = "IgniteTests24Java8_RunAll";

        /** */
        private String branchName = ITeamcity.DEFAULT;

        /** */
        private Date sinceDate = null;

        /** */
        private Date untilDate = null;

        /** */
        private DateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");

        public Builder(ITcBotConfig cfg) {
            // todo may find findDefaultBuildType() from cfg.getTeamcityConfig(srvCode).defaultTrackedBranch()
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
    }
}
