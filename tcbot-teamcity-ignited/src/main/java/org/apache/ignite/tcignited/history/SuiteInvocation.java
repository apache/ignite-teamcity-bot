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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import org.apache.ignite.cache.affinity.AffinityKeyMapped;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 * Shorter verison of FatBuild with less data: created only if run history was required,
 * has time limitation of MAX_DAYS, may have TTL.
 */
@Persisted
public class SuiteInvocation implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 2;

    /** Entity fields version. */
    @SuppressWarnings("FieldCanBeLocal")
    private short _ver = LATEST_VERSION;

    /** Server ID for queries */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 0)})
    private int srvId;

    /** Suite name for queries */
    @AffinityKeyMapped
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 1)})
    private int buildTypeId;

    /** Teamcity branch name for queries */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "serverSuiteBranch", order = 2)})
    private int normalizedBranchName;

    private Invocation suite;

    private Map<Integer, Invocation> tests = new HashMap<>();

    private Long buildStartTime;

    public SuiteInvocation() {}

    public SuiteInvocation(int srvId, int normalizedBaseBranch, FatBuildCompacted buildCompacted, IStringCompactor comp,
        BiPredicate<Integer, Integer> filter) {
        this.srvId = srvId;
        this.normalizedBranchName = normalizedBaseBranch;
        this.buildStartTime = buildCompacted.getStartDateTs();
        this.suite = buildCompacted.toInvocation(comp, filter);
        this.buildTypeId = buildCompacted.buildTypeId();
    }


    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public void addTest(int testName, Invocation invocation) {
        tests.put(testName, invocation);
    }

    public Map<Integer, Invocation> tests() {
        return Collections.unmodifiableMap(tests);
    }

    public Invocation suiteInvocation() {
        return suite;
    }
}
