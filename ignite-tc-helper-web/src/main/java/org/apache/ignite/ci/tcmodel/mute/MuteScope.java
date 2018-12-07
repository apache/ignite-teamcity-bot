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

package org.apache.ignite.ci.tcmodel.mute;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;

/**
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MuteScope {
    /** Project. */
    @XmlElement public Project project;

    /** Build types. */
    @XmlElementWrapper(name = "buildTypes")
    @XmlElement(name = "buildType")
    public List<BuildType> buildTypes;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        MuteScope scope = (MuteScope)o;

        if (project != null ? !project.equals(scope.project) : scope.project != null)
            return false;

        return buildTypes != null ? buildTypes.equals(scope.buildTypes) : scope.buildTypes == null;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = project != null ? project.hashCode() : 0;

        res = 31 * res + (buildTypes != null ? buildTypes.hashCode() : 0);

        return res;
    }
}
