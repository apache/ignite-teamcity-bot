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

package org.apache.ignite.ci;

import com.google.common.collect.Sets;
import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.util.Base64Util;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends ITeamcityConn {
    /** Default. */
    public String DEFAULT = "<default>";

    /** Branch synonym: Refs heads master. */
    public String REFS_HEADS_MASTER = "refs/heads/master";

    /** Branch synonym: Master. */
    public String MASTER = "master";

    /** Some fake property to avoid merging build in queue: Tcbot trigger time. */
    public String TCBOT_TRIGGER_TIME = "tcbot.triggerTime";

    /** Default list of properties to be skipped in saving in FAT builds. */
    public Set<String> AVOID_SAVE_PROPERTIES = Sets.newHashSet(TCBOT_TRIGGER_TIME, "build.query.loginTs");

    CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut);

    CompletableFuture<File> downloadBuildLogZip(int id);

    /**
     * Returns log analysis. Does not keep not zipped logs on disk.
     * @param buildId Build ID.
     * @param ctx Build results.
     * @return
     */
    CompletableFuture<LogCheckResult> analyzeBuildLog(Integer buildId, SingleBuildRunCtx ctx);

    void setExecutor(ExecutorService pool);

    Executor getExecutor();


    /**
     * @param tok TeamCity authorization token.
     */
    void setAuthToken(String tok);

    /**
     * @return {@code True} if TeamCity authorization token is available.
     */
    boolean isTeamCityTokenAvailable();

    default void setAuthData(String user, String pwd) {
        setAuthToken(
                Base64Util.encodeUtf8String(user + ":" + pwd));
    }

    User getUserByUsername(String username);
}
