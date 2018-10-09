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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.teamcity.pure.ITcServerProvider;
import org.apache.ignite.ci.teamcity.restcached.ITcServerFactory;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.ExceptionUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 */
class TcServerCachingProvider implements ITcServerProvider {
    /** Server factory. */
    @Inject
    private ITcServerFactory srvFactory;

    /** Servers. */
    private final Cache<String, IAnalyticsEnabledTeamcity> srvs
        = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();

    /** {@inheritDoc} */
    @Override public IAnalyticsEnabledTeamcity server(String srvId, @Nullable ICredentialsProv prov) {
        String fullKey = Strings.nullToEmpty(prov == null ? null : prov.getUser(srvId)) + ":" + Strings.nullToEmpty(srvId);

        try {
            return srvs.get(fullKey, () -> {
                IAnalyticsEnabledTeamcity teamcity = srvFactory.createServer(srvId);

                if (prov != null) {
                    final String user = prov.getUser(srvId);
                    final String pwd = prov.getPassword(srvId);
                    teamcity.setAuthData(user, pwd);
                }

                return teamcity;
            });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }
}
