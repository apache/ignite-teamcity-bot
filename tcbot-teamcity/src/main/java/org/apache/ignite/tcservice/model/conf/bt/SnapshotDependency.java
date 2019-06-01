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
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import org.apache.ignite.tcservice.model.conf.BuildType;

/**
 * Snapshot dependency.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SnapshotDependency {
    @XmlAttribute String id;
    @XmlAttribute String type;

    @XmlElement(name = "properties")
    Parameters properties;

    @XmlElement(name = "source-buildType")
    BuildType srcBt;

    public String getProperty(String id) {
        if (properties == null)
            return null;

        return properties.getParameter(id);
    }

    public BuildType bt() {
        return srcBt;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Parameters getProperties() {
        return properties;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setProperties(Parameters properties) {
        this.properties = properties;
    }

    public void setSrcBt(BuildType srcBt) {
        this.srcBt = srcBt;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof SnapshotDependency))
            return false;

        SnapshotDependency that = (SnapshotDependency)o;

        return Objects.equals(getId(), that.getId()) &&
            Objects.equals(getType(), that.getType()) &&
            Objects.equals(getProperties(), that.getProperties()) &&
            Objects.equals(srcBt, that.srcBt);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(getId(), getType(), getProperties(), srcBt);
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("type", type)
            .add("properties", properties)
            .add("srcBt", srcBt)
            .toString();
    }
}
