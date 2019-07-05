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

package org.apache.ignite.tcbot.engine.buildtime;

import java.util.List;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.ui.BuildTimeSummaryUi;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

public class BuildTimeService {

    @Inject ITeamcityIgnitedProvider tcProv;

    /** Config. */
    @Inject ITcBotConfig cfg;

    @Inject FatBuildDao fatBuildDao;

    public BuildTimeSummaryUi analytics(ICredentialsProv prov) {
        String serverCode = cfg.primaryServerCode();

        ITeamcityIgnited server = tcProv.server(serverCode, prov);

        fatBuildDao.forEachFatBuild();

        return null;
    }
}
