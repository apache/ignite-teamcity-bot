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

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.tcservice.http.ITeamcityHttpConnection;
import org.apache.ignite.tcservice.TcRealConnectionModule;
import org.jetbrains.annotations.Nullable;

/**
 * Guice module to setup TC connection With REST persistence
 */
public class TcRestCachedModule extends AbstractModule {
    /** Connection. */
    @Nullable private ITeamcityHttpConnection conn;

    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IAnalyticsEnabledTeamcity.class).to(IgnitePersistentTeamcity.class);
        bind(ITcServerFactory.class).to(InitializingServerFactory.class).in(new SingletonScope());

        bind(ITcServerProvider.class).to(TcServerCachingProvider.class).in(new SingletonScope());

        TcRealConnectionModule module = new TcRealConnectionModule();

        module.overrideHttp(conn);

        install(module);
    }

    public void overrideHttp(ITeamcityHttpConnection conn) {
        this.conn = conn;
    }
}
