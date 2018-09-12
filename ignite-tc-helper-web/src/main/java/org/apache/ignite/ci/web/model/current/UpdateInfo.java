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

package org.apache.ignite.ci.web.model.current;

import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;

/**
 * Created by Дмитрий on 19.02.2018
 */
@SuppressWarnings("WeakerAccess") public class UpdateInfo {
    /** TeamCity auth token availability flag. */
    public static int TEAMCITY_FLAG = 1;

    /** GitHub auth token availability flag. */
    public static int GITHUB_FLAG = 2;

    /** Flags to use in javascript. */
    public Integer javaFlags = 0;

    /** Update required, set by background updater. */
    public boolean updateRequired = false;

    /** Running updates is in progress, summary is ready, but it is subject to change */
    public int runningUpdates = 0;

    /** Hash code hexadecimal, protects from redraw and minimizing mode info in case data not changed */
    public String hashCodeHex;

    public UpdateInfo copyFrom(UpdateInfo info) {
        this.updateRequired = info.updateRequired;
        //todo there is no chance to update running futures if info is cached
        this.runningUpdates = info.runningUpdates;
        this.hashCodeHex = info.hashCodeHex;

        return this;
    }

    /**
     * @param teamcity TeamCity to get info about tokens.
     */
    public void setJavaFlags(IAnalyticsEnabledTeamcity teamcity) {
        if (teamcity.isTeamCityTokenAvailable())
            javaFlags = javaFlags | TEAMCITY_FLAG;

        if (teamcity.isGitTokenAvailable())
            javaFlags = javaFlags | GITHUB_FLAG;
    }
}
