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

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeSync;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeSync;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProactiveFatBuildSync;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.teamcity.restcached.TcRestCachedModule;
import org.jetbrains.annotations.Nullable;

/**
 * Guice module to setup real connected server and all related implementations.
 */
public class TeamcityIgnitedModule extends AbstractModule {
    /** Connection. */
    @Nullable private ITeamcityHttpConnection conn;

    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(ITeamcityIgnitedProvider.class).to(TcIgnitedCachingProvider.class).in(new SingletonScope());
        bind(BuildRefDao.class).in(new SingletonScope());
        bind(BuildConditionDao.class).in(new SingletonScope());
        bind(FatBuildDao.class).in(new SingletonScope());
        bind(ProactiveFatBuildSync.class).in(new SingletonScope());
        bind(ChangeSync.class).in(new SingletonScope());
        bind(ChangeDao.class).in(new SingletonScope());
        bind(BuildTypeRefDao.class).in(new SingletonScope());
        bind(BuildTypeDao.class).in(new SingletonScope());
        bind(BuildTypeSync.class).in(new SingletonScope());
        bind(IStringCompactor.class).to(IgniteStringCompactor.class).in(new SingletonScope());

        TcRestCachedModule module = new TcRestCachedModule();
        module.overrideHttp(conn);
        install(module);
    }

    public void overrideHttp(ITeamcityHttpConnection conn) {
        this.conn = conn;
    }
}
