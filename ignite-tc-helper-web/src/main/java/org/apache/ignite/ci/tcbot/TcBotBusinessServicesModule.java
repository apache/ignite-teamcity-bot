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
package org.apache.ignite.ci.tcbot;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.tcbot.engine.buildtime.BuildTimeService;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.LocalFilesBasedConfig;
import org.apache.ignite.ci.tcbot.issue.IIssuesStorage;
import org.apache.ignite.ci.tcbot.trends.MasterTrendsService;
import org.apache.ignite.ci.tcbot.user.IUserStorage;
import org.apache.ignite.ci.tcbot.user.UserAndSessionsStorage;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;

/**
 * TC Bot self services mapping (without 3rd party integrations configuration.
 */
public class TcBotBusinessServicesModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(ITcBotConfig.class).to(LocalFilesBasedConfig.class).in(new SingletonScope());
        //todo remove
        bind(IDataSourcesConfigSupplier.class).to(LocalFilesBasedConfig.class).in(new SingletonScope());
        bind(IUserStorage.class).to(UserAndSessionsStorage.class).in(new SingletonScope());
        bind(IIssuesStorage.class).to(IssuesStorage.class).in(new SingletonScope());
        bind(MasterTrendsService.class).in(new SingletonScope());
        bind(ITcBotBgAuth.class).to(TcBotBgAuthImpl.class).in(new SingletonScope());
        bind(BuildChainProcessor.class).in(new SingletonScope());

        //todo move to bot engine module
        bind(BuildTimeService.class).in(new SingletonScope());
    }
}
