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

package org.apache.ignite.tcservice.model.conf.bt;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * List of build snapshot dependencies.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SnapshotDependencies {
    @XmlAttribute Integer count;

    @XmlElement(name="snapshot-dependency")
    List<SnapshotDependency> list = new ArrayList<>();

    public SnapshotDependencies() {
    }

    public SnapshotDependencies(List<SnapshotDependency> list) {
        if (list != null) {
            this.list = list;
            this.count = list.size();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof SnapshotDependencies))
            return false;

        SnapshotDependencies that = (SnapshotDependencies)o;

        return Objects.equals(count, that.count) &&
            Objects.equals(list, that.list);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(count, list);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("count", count)
            .add("list", list)
            .toString();
    }
}
