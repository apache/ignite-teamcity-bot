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

package org.apache.ignite.ci.teamcity.ignited;

import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamcityIgnitedProviderMock implements ITeamcityIgnitedProvider {
    /** Compactor. */
    @Inject
    IStringCompactor compactor;

    private Map<String, Map<Integer, FatBuildCompacted>> tcBuildsData = new ConcurrentHashMap<>();

    public void addServer(String srvId, Map<Integer, FatBuildCompacted> apacheBuilds) {
        tcBuildsData.put(srvId, apacheBuilds);
    }

    /** {@inheritDoc} */
    @Override public boolean hasAccess(String srvCode, @Nullable ICredentialsProv prov) {
        return prov != null && prov.hasAccess(srvCode);
    }

    /** {@inheritDoc} */
    @Override public ITeamcityIgnited server(String srvCode, ICredentialsProv prov) {
        final Map<Integer, FatBuildCompacted> integerFatBuildCompactedMap = tcBuildsData.get(srvCode);

        return TeamcityIgnitedMock.getMutableMapTeamcityIgnited(integerFatBuildCompactedMap, compactor);
    }
}
