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

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ignite.ci.tcbot.condition.BuildCondition;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;

/**
 *
 */
public interface ITeamcityIgnited {
    /**
     * @return Internal server ID as string
     */
    String serverId();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    /**
     * Retun all builds for branch and suite, without relation to its status.
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @return list of builds in history, includes all statuses: queued, running, etc
     */
    public List<BuildRefCompacted> getBuildHistoryCompacted(
            @Nullable String buildTypeId,
            @Nullable String branchName);


    /**
     * Retun all builds for branch and suite, without relation to its status.
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @return list of builds in history, includes all statuses: queued, running, etc
     */
    public List<BuildRef> getBuildHistory(
        @Nullable String buildTypeId,
        @Nullable String branchName);

    /**
     * Trigger build. Enforces TC Bot to load all builds related to this triggered one.
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     */
    public Build triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop);

    /**
     * @param srvId Server id.
     * @return integer representation of server ID.
     */
    public static int serverIdToInt(String srvId) {
        return Math.abs(srvId.hashCode());
    }

    /**
     * Check build condition.
     *
     * @param buildId Build id.
     */
    public boolean buildIsValid(int buildId);

    /**
     * Set build condition.
     *
     * @param cond Condition.
     */
    public boolean setBuildCondition(BuildCondition cond);

    /**
     * @param id Id.
     */
    public FatBuildCompacted getFatBuild(int id);

    public Collection<ChangeCompacted> getAllChanges(int[] changeIds);
}
