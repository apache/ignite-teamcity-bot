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

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.internal.util.typedef.F;

/**
 *
 */
public class MuteScopeCompacted {
    /** Project. Project id. */
    int projectId;

    /** Project. Project name. */
    int projectName;

    /** Build types. */
    List<BuildTypeRefCompacted> buildTypes;

    /**
     * @param scope Mute scope.
     * @param comp Compactor.
     */
    MuteScopeCompacted(MuteScope scope, IStringCompactor comp) {
        if (scope.project != null) {
            projectId = comp.getStringId(scope.project.id);
            projectName = comp.getStringId(scope.project.name);
        }

        if (!F.isEmpty(scope.buildTypes)) {
            buildTypes = new ArrayList<>(scope.buildTypes.size());

            for (BuildType bt : scope.buildTypes)
                buildTypes.add(new BuildTypeRefCompacted(comp, bt));
        }
    }

    /**
     * @return Mute scope.
     */
    MuteScope toMuteScope(IStringCompactor comp) {
        MuteScope scope = new MuteScope();

        if (projectId > 0 && projectName > 0) {
            scope.project = new Project();
            scope.project.id = comp.getStringFromId(projectId);
            scope.project.name = comp.getStringFromId(projectName);
        }

        if (!F.isEmpty(buildTypes)) {
            for (BuildTypeRefCompacted bt : buildTypes) {
                scope.buildTypes = new ArrayList<>();

                scope.buildTypes.add(bt.toBuildTypeRef(comp));
            }
        }

        return scope;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        MuteScopeCompacted compacted = (MuteScopeCompacted)o;

        if (projectId != compacted.projectId)
            return false;
        if (projectName != compacted.projectName)
            return false;

        return buildTypes != null ? buildTypes.equals(compacted.buildTypes) : compacted.buildTypes == null;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = projectId;

        res = 31 * res + projectName;
        res = 31 * res + (buildTypes != null ? buildTypes.hashCode() : 0);

        return res;
    }
}