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
package org.apache.ignite.ci.teamcity.restcached;

import com.google.common.base.Strings;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.web.TcUpdatePool;

 class InitializingServerFactory implements ITcServerFactory {
    @Inject
    Provider<IAnalyticsEnabledTeamcity> tcPersistProv;

    @Inject
    Provider<ITeamcity> tcConnProv;

    @Inject
    private TcUpdatePool tcUpdatePool;

    /** {@inheritDoc} */
    @Override public IAnalyticsEnabledTeamcity createServer(String srvId) {
        ITeamcity tcConn = tcConnProv.get();
        tcConn.init(Strings.emptyToNull(srvId));

        IAnalyticsEnabledTeamcity instance = tcPersistProv.get();
        instance.init(tcConn);

        instance.setExecutor(tcUpdatePool.getService());

        return instance;
    }
}
