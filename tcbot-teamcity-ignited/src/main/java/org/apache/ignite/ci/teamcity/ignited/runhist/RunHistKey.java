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

package org.apache.ignite.ci.teamcity.ignited.runhist;

import com.google.common.base.Objects;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 *
 */
public class RunHistKey {
    /** Server ID. */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "tstAndSrv", order = 1)})
    private int srvId;

    /** Test name or suite build type ID. */
    @QuerySqlField(orderedGroups = {@QuerySqlField.Group(name = "tstAndSrv", order = 0)})
    private int testOrSuiteName;

    /** Branch name. */
    private int branch;

    /**
     * @param srvId Server id.
     * @param testOrSuiteName Test or suite name.
     * @param branchName Branch name.
     */
    public RunHistKey(int srvId, int testOrSuiteName, int branchName) {
        this.srvId = srvId;
        this.testOrSuiteName = testOrSuiteName;
        this.branch = branchName;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RunHistKey histKey = (RunHistKey)o;
        return srvId == histKey.srvId &&
            testOrSuiteName == histKey.testOrSuiteName &&
            branch == histKey.branch;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(srvId, testOrSuiteName, branch);
    }

    /**  */
    public int testNameOrSuite() {
        return testOrSuiteName;
    }

    /** */
    public int srvId() {
        return srvId;
    }

    /** */
    public int branch() {
        return branch;
    }
}
