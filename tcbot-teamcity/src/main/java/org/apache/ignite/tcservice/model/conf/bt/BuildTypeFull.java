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

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Full build type settings.
 */
@XmlRootElement(name = "buildType")
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildTypeFull extends org.apache.ignite.tcservice.model.conf.BuildType {
    @XmlElement(name = "settings")
    Parameters settings;

    @XmlElement(name = "parameters")
    Parameters parameters;

    @XmlElement(name = "snapshot-dependencies")
    SnapshotDependencies snapshotDependencies;

    @Nullable
    public String getParameter(String key) {
        if (parameters == null)
            return null;
        return parameters.getParameter(key);
    }

    public boolean setSetting(String key, String value) {
        if (settings == null)
            return false;

        return settings.setParameter(key, value);
    }

    public List<SnapshotDependency> dependencies() {
        if (snapshotDependencies == null)
            return Collections.emptyList();

        final List<SnapshotDependency> list = snapshotDependencies.list;
        if (list == null)
            return Collections.emptyList();

        return list;
    }

    public Parameters getSettings() {
        return settings;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void setSettings(Parameters settings) {
        this.settings = settings;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public void setSnapshotDependencies(SnapshotDependencies snapshotDependencies) {
        this.snapshotDependencies = snapshotDependencies;
    }
}
