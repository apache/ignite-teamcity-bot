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

package org.apache.ignite.ci.teamcity.ignited.buildtype;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.ignite.tcservice.model.conf.bt.SnapshotDependency;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public class SnapshotDependencyCompacted {
    /** Id. */
    private int id = -1;

    /** Type. */
    private int type = -1;

    /** Reference to source-buildType. */
    private BuildTypeRefCompacted buildTypeRefCompacted;

    /** Properties. */
    private ParametersCompacted properties;

    /**
     * Default constructor.
     */
    public SnapshotDependencyCompacted(){
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public SnapshotDependencyCompacted(IStringCompactor compactor, SnapshotDependency ref) {
        id = compactor.getStringId(ref.getId());
        type = compactor.getStringId(ref.getType());
        buildTypeRefCompacted = new BuildTypeRefCompacted(compactor, ref.bt());
        properties = new ParametersCompacted(compactor, ref.getProperties().properties());
    }

    /**
     * @param compactor Compacter.
     */
    public SnapshotDependency toSnapshotDependency(IStringCompactor compactor) {
        SnapshotDependency ref = new SnapshotDependency();

        fillSnapshotDependencyCompacted(compactor, ref);

        return ref;
    }

    /**
     * @param compactor Compacter.
     * @param res Response.
     */
    protected void fillSnapshotDependencyCompacted(IStringCompactor compactor, SnapshotDependency res) {
        res.setId(compactor.getStringFromId(id));
        res.setType(compactor.getStringFromId(type));
        res.setSrcBt(buildTypeRefCompacted.toBuildTypeRef(compactor));
        res.setProperties(properties.toParameters(compactor));
    }

    public int id() {
        return id;
    }

    public void id(int id) {
        this.id = id;
    }

    public int type() {
        return type;
    }

    public void type(int type) {
        this.type = type;
    }

    public ParametersCompacted properties() {
        return properties;
    }

    public void properties(ParametersCompacted properties) {
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof SnapshotDependencyCompacted))
            return false;

        SnapshotDependencyCompacted compacted = (SnapshotDependencyCompacted)o;

        return id == compacted.id &&
            type == compacted.type &&
            Objects.equals(buildTypeRefCompacted, compacted.buildTypeRefCompacted) &&
            Objects.equals(properties, compacted.properties);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(id, type, buildTypeRefCompacted, properties);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("type", type)
            .add("buildTypeRefCompacted", buildTypeRefCompacted)
            .add("properties", properties)
            .toString();
    }
}
