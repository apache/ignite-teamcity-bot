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

package org.apache.ignite.tcservice.model.user;


import java.util.Collection;
import org.apache.ignite.tcservice.model.conf.bt.Parameters;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "user")
public class User extends UserRef {
    @XmlAttribute
    public String email;
    @XmlAttribute
    public String lastLogin;

    @XmlElement(name = "parameters")
    Parameters parameters;

    @XmlElement(name = "groups")
    private Groups groups;

    /**
     * @return space separated list of vcs user names
     */
    @Nullable
    String getVcsNames() {
        if (parameters == null)
            return null;

        return parameters.getParameter("plugin:vcs:anyVcs:anyVcsRoot");
    }

    /**
     * @param groups Groups.
     */
    public void setGroups(Groups groups) {
        this.groups = groups;
    }

    /**
     * @param groupIds TeamCity group ids.
     */
    public boolean belongsToAnyGroup(Collection<String> groupIds) {
        if (groups == null || groupIds == null || groupIds.isEmpty())
            return false;

        return groups.getGroupRefs().stream().anyMatch(grp ->
            groupIds.stream().anyMatch(configured -> matchesGroupId(configured, grp.key)));
    }

    /**
     * @param configured Configured group id.
     * @param actualId Actual group id returned by TeamCity as a REST key.
     */
    private boolean matchesGroupId(String configured, String actualId) {
        if (configured == null || actualId == null)
            return false;

        return configured.trim().equals(actualId.trim());
    }
}
