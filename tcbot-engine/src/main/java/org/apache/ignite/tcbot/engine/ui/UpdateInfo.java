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

package org.apache.ignite.tcbot.engine.ui;

import java.util.Map;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;

/**
 * General update information for JS data updating requests. UI model, so it contains public fields.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"}) public class UpdateInfo {
    /** TeamCity auth token availability flag. */
    public static final int TEAMCITY_FLAG = 1;

    /** GitHub auth token availability flag. */
    public static final int GITHUB_FLAG = 2;

    /** JIRA auth token availability flag. */
    public static final int JIRA_FLAG = 4;

    /** Flags to use in javascript. */
    public Integer javaFlags = 0;

    /** Running updates is in progress, not used since it should be updated using counters */
    @Deprecated
    public int runningUpdates = 0;

    /** Hash code hexadecimal, protects from redraw and minimizing mode info in case data not changed */
    public String hashCodeHex;

    /** Update Counters for all related TC Branches. */
    public Map<Integer, Integer> counters;

    /**
     * @param gitHubCfg
     * @param jiraCfg
     */
    public void setJavaFlags(IGitHubConfig gitHubCfg, IJiraServerConfig jiraCfg) {
        //since user has logged in, TC flag should be set
        javaFlags |= TEAMCITY_FLAG;

        if (gitHubCfg.isGitTokenAvailable())
            javaFlags |= GITHUB_FLAG;

        if (jiraCfg.isJiraTokenAvailable())
            javaFlags |= JIRA_FLAG;
    }

    public UpdateInfo initCounters(Map<Integer, Integer> counters) {
        this.counters = counters;
        hashCodeHex = UpdateCountersStorage.getCountersHash(counters);

        return this;
    }
}
