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
package org.apache.ignite.tcignited;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcservice.TeamcityServiceConnection;

/**
 *
 */
class TcIgnitedCachingProvider implements ITeamcityIgnitedProvider {
    /** Server factory. */
    @Inject private Provider<TeamcityServiceConnection> srvFactory;

    /** Config. */
    @Inject private IDataSourcesConfigSupplier cfg;

    @Inject private Provider<TeamcityIgnitedImpl> provider;

    private final Cache<String, ITeamcityIgnited> srvs
            = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(16, TimeUnit.MINUTES)
            .softValues()
            .build();

    /** {@inheritDoc} */
    @Override public boolean hasAccess(String srvCode, @Nullable ICredentialsProv prov) {
        if (prov == null)
            return false;

        ITcServerConfig tcSrvCfg = cfg.getTeamcityConfig(srvCode);

        if (!Strings.isNullOrEmpty(tcSrvCfg.additionalServiceToCheckAccess())) {
            if (!prov.hasAccess(tcSrvCfg.additionalServiceToCheckAccess()))
                return false;
        }

        String ref = tcSrvCfg.reference();

        if (!Strings.isNullOrEmpty(ref))
            return prov.hasAccess(ref);

        return prov.hasAccess(srvCode);
    }

    /** {@inheritDoc} */
    @Override public ITeamcityIgnited server(String srvCode, @Nullable ICredentialsProv prov) {
        ITcServerConfig cfg = this.cfg.getTeamcityConfig(srvCode);
        String ref = cfg.reference();

        String realSrvCode = !Strings.isNullOrEmpty(ref) && !srvCode.equals(ref) ? ref : srvCode;

        String fullKey = Strings.nullToEmpty(prov == null ? null : prov.getUser(realSrvCode)) + ":" + Strings.nullToEmpty(realSrvCode);

        try {
            return srvs.get(fullKey, () -> {
                final TeamcityServiceConnection teamcityServiceConnection = srvFactory.get();
                teamcityServiceConnection.init(realSrvCode);

                if (prov != null) {
                    String user = prov.getUser(realSrvCode);
                    String pwd = prov.getPassword(realSrvCode);

                    teamcityServiceConnection.setAuthData(user, pwd);
                }

                TeamcityIgnitedImpl impl = provider.get();

                impl.init(teamcityServiceConnection);

                return impl;
            });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }
}
