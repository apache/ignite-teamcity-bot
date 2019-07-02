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
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.digest.DigestService;
import org.apache.ignite.tcbot.engine.tracked.IDetailedStatusForTrackedBranch;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;

/**
 *
 */
public class TcBotEngineModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(BuildChainProcessor.class).in(new SingletonScope());
        bind(IDetailedStatusForTrackedBranch.class).to(TrackedBranchChainsProcessor.class).in(new SingletonScope());
        bind(DigestService.class).in(new SingletonScope());
    }
}