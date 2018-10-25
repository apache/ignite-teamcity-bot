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

package org.apache.ignite.ci.teamcity.pure;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;

/**
 * Pure Teamcity Connection API for calling methods from REST service: https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcityConn {
    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    public Build getBuild(int buildId);

    public List<BuildRef> getBuildRefs(String fullUrl, AtomicReference<String> nextPage);

    /**
     * Trigger build.
     *
     * @param buildTypeId Build identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     */
    public Build triggerBuild(String buildTypeId, @Nonnull String branchName, boolean cleanRebuild, boolean queueAtTop);
}
