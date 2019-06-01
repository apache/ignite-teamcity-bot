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

package org.apache.ignite.tcservice.model.conf;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.tcservice.model.result.AbstractRef;

/**
 * Build type reference
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildType extends AbstractRef {
    @XmlAttribute private String id;

    @XmlAttribute private String name;

    @XmlAttribute private String projectId;

    @XmlAttribute private String projectName;

    @XmlAttribute private String webUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildType))
            return false;

        BuildType ref = (BuildType)o;

        return Objects.equals(getId(), ref.getId()) &&
            Objects.equals(getName(), ref.getName()) &&
            Objects.equals(getProjectId(), ref.getProjectId()) &&
            Objects.equals(getProjectName(), ref.getProjectName()) &&
            Objects.equals(getWebUrl(), ref.getWebUrl());
    }

    @Override public int hashCode() {
        return Objects.hash(getId(), getName(), getProjectId(), getProjectName(), getWebUrl());
    }

    @Override public String toString() {
        return "BuildTypeRef{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", projectId='" + projectId + '\'' +
            ", projectName='" + projectName + '\'' +
            ", webUrl='" + webUrl + '\'' +
            '}';
    }
}
