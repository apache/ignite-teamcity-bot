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

import java.util.List;
import java.util.function.Function;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;

@Deprecated
public interface ITcAnalytics {
    /**
     * Return build statistics for default branch provider
     *
     * @return map from suite ID to its run statistics
     */
    @Deprecated
    Function<SuiteInBranch, RunStat> getBuildFailureRunStatProvider();

    /**
     * @return map from test full name (suite: suite.test) and its branch to its run statistics
     */
    @Deprecated
    Function<TestInBranch, RunStat> getTestRunStatProvider();

    String getThreadDumpCached(Integer buildId);

    /**
     * Calculate required statistic for build if was not already calculated.
     *
     * @param ctx Context as provider build data.
     */
    @Deprecated
    void calculateBuildStatistic(SingleBuildRunCtx ctx);
}
