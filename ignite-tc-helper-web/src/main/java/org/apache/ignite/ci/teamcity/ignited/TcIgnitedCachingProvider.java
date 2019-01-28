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

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.ITcServerConfig;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.ExceptionUtil;

/**
 *
 */
class TcIgnitedCachingProvider implements ITeamcityIgnitedProvider {
    /** Server factory. */
    @Inject
    private ITcServerProvider srvFactory;

    @Inject private ITcBotConfig config;

    @Inject private Provider<TeamcityIgnitedImpl> provider;

    private final Cache<String, ITeamcityIgnited> srvs
            = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(16, TimeUnit.MINUTES)
            .softValues()
            .build();

    /** {@inheritDoc} */
    @Override public ITeamcityIgnited server(String srvIdReq, @Nullable ICredentialsProv prov) {
        ITcServerConfig cfg = config.getTeamcityConfig(srvIdReq);
        String ref = cfg.reference();

        String realSrvId = !Strings.isNullOrEmpty(ref) && !srvIdReq.equals(ref) ? ref : srvIdReq;

        String fullKey = Strings.nullToEmpty(prov == null ? null : prov.getUser(realSrvId)) + ":" + Strings.nullToEmpty(realSrvId);

        try {
            return srvs.get(fullKey, () -> {
                ITeamcity tcRealConn = srvFactory.server(realSrvId, prov);

                if (prov != null) {
                    final String user = prov.getUser(realSrvId);
                    final String pwd = prov.getPassword(realSrvId);
                    tcRealConn.setAuthData(user, pwd);
                }

                TeamcityIgnitedImpl impl = provider.get();

                impl.init(realSrvId, tcRealConn);

                return impl;
            });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }
}
