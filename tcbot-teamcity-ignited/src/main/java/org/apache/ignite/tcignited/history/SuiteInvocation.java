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

import java.util.List;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 * Shorter verison of FatBuild with less data: created only if run history was required,
 * has time limitation of MAX_DAYS, may have TTL.
 */
@Persisted
public class SuiteInvocation {
    /** Server ID for queries */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 0)})
    private int srvId;

    /** Suite name for queries */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 1)})
    private int suiteName;

    /** Teamcity branch name for queries */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 2)})
    private int normalizedBranchName;

    Invocation suite;

    List<Invocation> tests;

    Long buildStartTime;
}
