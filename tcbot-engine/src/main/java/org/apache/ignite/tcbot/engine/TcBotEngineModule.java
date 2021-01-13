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

package org.apache.ignite.tcbot.engine;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.tcbot.common.TcBotCommonModule;
import org.apache.ignite.tcbot.engine.board.BoardService;
import org.apache.ignite.tcbot.engine.boardmute.MutedIssuesDao;
import org.apache.ignite.tcbot.engine.build.SingleBuildResultsService;
import org.apache.ignite.tcbot.engine.buildtime.BuildTimeService;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
import org.apache.ignite.tcbot.engine.tracked.IDetailedStatusForTrackedBranch;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcbot.engine.user.UserAndSessionsStorage;

/**
 *
 */
public class TcBotEngineModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(BuildChainProcessor.class).in(new SingletonScope());
        bind(IDetailedStatusForTrackedBranch.class).to(TrackedBranchChainsProcessor.class).in(new SingletonScope());
        bind(SingleBuildResultsService.class).in(new SingletonScope());

        bind(BuildTimeService.class).in(new SingletonScope());

        bind(IIssuesStorage.class).to(IssuesStorage.class).in(new SingletonScope());

        bind(BoardService.class).in(new SingletonScope());

        bind(IUserStorage.class).to(UserAndSessionsStorage.class).in(new SingletonScope());

        bind(MutedIssuesDao.class).in(new SingletonScope());
        bind(NewTestsStorage.class).in(new SingletonScope());

        install(new TcBotCommonModule());
    }
}
