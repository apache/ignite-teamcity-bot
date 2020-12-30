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
import com.google.inject.internal.SingletonScope;
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
        bind(BuildRefDao.class).in(new SingletonScope());
        bind(BuildRefSync.class).in(new SingletonScope());
        bind(BuildConditionDao.class).in(new SingletonScope());
        bind(FatBuildDao.class).in(new SingletonScope());
        bind(ProactiveFatBuildSync.class).in(new SingletonScope());
        bind(ChangeSync.class).in(new SingletonScope());
        bind(ChangeDao.class).in(new SingletonScope());
        bind(BuildTypeRefDao.class).in(new SingletonScope());
        bind(BuildTypeDao.class).in(new SingletonScope());
        bind(BuildTypeSync.class).in(new SingletonScope());
        bind(BuildStartTimeStorage.class).in(new SingletonScope());
        bind(MuteDao.class).in(new SingletonScope());
        bind(MuteSync.class).in(new SingletonScope());
        bind(BuildLogCheckResultDao.class).in(new SingletonScope());
        bind(SuiteInvocationHistoryDao.class).in(new SingletonScope());
        bind(HistoryCollector.class).in(new SingletonScope());
        bind(ILogProductSpecific.class).to(LogIgniteSpecific.class).in(new SingletonScope());
        bind(UpdateCountersStorage.class).in(new SingletonScope());
        bind(Cleaner.class).in(new SingletonScope());
        bind(DefectsStorage.class).in(new SingletonScope());
        bind(IIssuesStorage.class).to(IssuesStorage.class).in(new SingletonScope());

        TcRealConnectionModule module = new TcRealConnectionModule();

        install(module);

        install(new BuildLogProcessorModule());
    }
}
