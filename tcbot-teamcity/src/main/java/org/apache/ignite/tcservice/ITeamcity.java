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

package org.apache.ignite.tcservice;

import com.google.common.collect.Sets;
import org.apache.ignite.tcbot.common.util.Base64Util;
import org.apache.ignite.tcservice.model.user.User;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

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

    /** Fake property for addressing 'Suite id'. */
    public String SUITE_ID_PROPERTY = "_suiteId";

    /** Fake property for addressing 'Suite Name'. */
    public String SUITE_NAME_PROPERTY = "_suiteName";

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

    /**
     * @param username Username.
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
     */
    User getUserByUsername(String username);
}
