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
package org.apache.ignite.tcbot.engine.conf;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;

/**
 *
 */
public interface ITrackedChain {
    /**
     * @return Server Code to access configs within IDataSourceCfgSupplier.
     */
    public String serverCode();

    public String tcBranch();

    @Nonnull public Optional<String> tcBaseBranch();

    /**
     * @return Quiet period in minutes between triggering builds or zero if period is not set and should be ignored.
     */
    public int triggerBuildQuietPeriod();

    @Nonnull
    public Map<String, Object> generateBuildParameters();

    public String tcSuiteId();

    /**
     * @return {@code True} If automatic build triggering enabled.
     */
    public boolean triggerBuild();
}
