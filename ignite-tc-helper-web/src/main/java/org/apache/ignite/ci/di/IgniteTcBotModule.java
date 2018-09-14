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
package org.apache.ignite.ci.di;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.matcher.Matchers;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.IgniteTeamcityHelper;

public class IgniteTcBotModule extends AbstractModule {
    @Deprecated
    private Ignite ignite;

    protected void configure() {
        bindInterceptor(Matchers.any(),
                Matchers.annotatedWith(AutoProfiling.class),
                new ProfilingInterceptor());

        bind(Ignite.class).toProvider(new Provider<Ignite>() {
            @Override
            public Ignite get() {
                return ignite;
            }
        });

        bind(IAnalyticsEnabledTeamcity.class).to(IgnitePersistentTeamcity.class);
        bind(IServerProv.class).toInstance(
                new MyIServerProv()
        );
    }

    @Deprecated
    public void setIgnite(Ignite ignite) {

        this.ignite = ignite;
    }

    private class MyIServerProv implements IServerProv {
        @Inject
        Injector injector;


        @Override
        public IAnalyticsEnabledTeamcity createServer(String serverId) {
            IgniteTeamcityHelper igniteTeamcityHelper = new IgniteTeamcityHelper(Strings.emptyToNull(serverId));

            IAnalyticsEnabledTeamcity instance = injector.getInstance(IAnalyticsEnabledTeamcity.class);
            instance.init(igniteTeamcityHelper);

            return instance;
        }
    }
}
