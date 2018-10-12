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
import org.apache.ignite.ci.tcmodel.hist.BuildRef;

public class BuildRefCompacted {
    int id = -1;
    int buildTypeId = -1;
    int branchName = -1;
    int status = -1;
    int state = -1;

    /**
     * Default constructor.
     */
    public BuildRefCompacted() {
    }

    /**
     * @param compacter Compacter.
     * @param ref Reference.
     */
    public BuildRefCompacted(IStringCompactor compacter, BuildRef ref) {
        id = ref.getId() == null ? -1 : ref.getId();
        buildTypeId = compacter.getStringId(ref.buildTypeId());
        branchName = compacter.getStringId(ref.branchName());
        status = compacter.getStringId(ref.status());
        state = compacter.getStringId(ref.state());
    }

    /**
     * @param compactor Compacter.
     */
    public BuildRef toBuildRef(IStringCompactor compactor) {
        BuildRef res = new BuildRef();

        res.setId(id < 0 ? null : id);
        res.buildTypeId = compactor.getStringFromId(buildTypeId);
        res.branchName = compactor.getStringFromId(branchName);
        res.status = compactor.getStringFromId(status);
        res.state = compactor.getStringFromId(state);

        return res;
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

    /**
     *
     */
    public int id() {
        return id;
    }
}
