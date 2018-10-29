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

import com.google.common.base.Objects;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.jetbrains.annotations.NotNull;

@Persisted
public class BuildRefCompacted {
    /** Build Id without modifications, -1 if it is null. */
    private int id = -1;

    /** Compacter identifier for string 'Build type id'. */
    private int buildTypeId = -1;

    /** Compacter identifier for string 'Branch name'. */
    @QuerySqlField(index = true)
    private int branchName = -1;

    /** Compacter identifier for string 'Status'. */
    private int status = -1;

    /** Compacter identifier for string 'State'. */
    private int state = -1;

    /**
     * Default constructor.
     */
    public BuildRefCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public BuildRefCompacted(IStringCompactor compactor, BuildRef ref) {
        id = ref.getId() == null ? -1 : ref.getId();
        buildTypeId = compactor.getStringId(ref.buildTypeId());
        branchName = compactor.getStringId(ref.branchName());
        status = compactor.getStringId(ref.status());
        state = compactor.getStringId(ref.state());
    }

    /**
     * @param refCompacted Reference compacted.
     */
    public BuildRefCompacted(BuildRefCompacted refCompacted) {
        id = refCompacted.id();
        buildTypeId = refCompacted.buildTypeId();
        branchName = refCompacted.branchName();
        status = refCompacted.status();
        state = refCompacted.state();
    }


    /**
     * @param compactor Compacter.
     */
    public BuildRef toBuildRef(IStringCompactor compactor) {
        BuildRef res = new BuildRef();

        fillBuildRefFields(compactor, res);

        return res;
    }

    protected void fillBuildRefFields(IStringCompactor compactor, BuildRef res) {
        res.setId(id < 0 ? null : id);
        res.buildTypeId = compactor.getStringFromId(buildTypeId);
        res.branchName = compactor.getStringFromId(branchName);
        res.status = compactor.getStringFromId(status);
        res.state = compactor.getStringFromId(state);
        res.href = getHrefForId(id());
    }

    @NotNull protected static String getHrefForId(int id) {
        return "/app/rest/latest/builds/id:" + id;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BuildRefCompacted compacted = (BuildRefCompacted)o;
        return id == compacted.id &&
            buildTypeId == compacted.buildTypeId &&
            branchName == compacted.branchName &&
            status == compacted.status &&
            state == compacted.state;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(id, buildTypeId, branchName, status, state);
    }

    /** */
    public int id() {
        return id;
    }

    /** */
    public int buildTypeId() {
        return buildTypeId;
    }

    /** */
    public int branchName() {
        return branchName;
    }

    /** */
    private int status() {
        return status;
    }

    /** */
    public int state() {
        return state;
    }

    /** */
    public boolean isFakeStub() {
        return id() < 0;
    }
}
