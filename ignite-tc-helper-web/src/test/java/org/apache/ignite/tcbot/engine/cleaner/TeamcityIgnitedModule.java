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

package org.apache.ignite.tcbot.engine.cleaner;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeSync;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeSync;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.ProactiveFatBuildSync;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildlog.BuildLogProcessorModule;
import org.apache.ignite.tcignited.buildlog.ILogProductSpecific;
import org.apache.ignite.tcignited.buildlog.LogIgniteSpecific;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.buildref.BuildRefSync;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.apache.ignite.tcignited.mute.MuteDao;
import org.apache.ignite.tcignited.mute.MuteSync;
import org.apache.ignite.tcservice.TcRealConnectionModule;

public class TeamcityIgnitedModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(BuildRefDao.class).in(Scopes.SINGLETON);
        bind(BuildRefSync.class).in(Scopes.SINGLETON);
        bind(BuildConditionDao.class).in(Scopes.SINGLETON);
        bind(FatBuildDao.class).in(Scopes.SINGLETON);
        bind(ProactiveFatBuildSync.class).in(Scopes.SINGLETON);
        bind(ChangeSync.class).in(Scopes.SINGLETON);
        bind(ChangeDao.class).in(Scopes.SINGLETON);
        bind(BuildTypeRefDao.class).in(Scopes.SINGLETON);
        bind(BuildTypeDao.class).in(Scopes.SINGLETON);
        bind(BuildTypeSync.class).in(Scopes.SINGLETON);
        bind(BuildStartTimeStorage.class).in(Scopes.SINGLETON);
        bind(MuteDao.class).in(Scopes.SINGLETON);
        bind(MuteSync.class).in(Scopes.SINGLETON);
        bind(BuildLogCheckResultDao.class).in(Scopes.SINGLETON);
        bind(SuiteInvocationHistoryDao.class).in(Scopes.SINGLETON);
        bind(HistoryCollector.class).in(Scopes.SINGLETON);
        bind(ILogProductSpecific.class).to(LogIgniteSpecific.class).in(Scopes.SINGLETON);
        bind(UpdateCountersStorage.class).in(Scopes.SINGLETON);
        bind(Cleaner.class).in(Scopes.SINGLETON);
        bind(DefectsStorage.class).in(Scopes.SINGLETON);
        bind(IIssuesStorage.class).to(IssuesStorage.class).in(Scopes.SINGLETON);

        TcRealConnectionModule module = new TcRealConnectionModule();

        install(module);

        install(new BuildLogProcessorModule());
    }
}
