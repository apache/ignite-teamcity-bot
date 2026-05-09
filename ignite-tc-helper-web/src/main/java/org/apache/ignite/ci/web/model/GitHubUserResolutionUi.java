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

package org.apache.ignite.ci.web.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.tcbot.visa.GitHubUserResolver;

/** GitHub profile resolution for current bot user. */
@SuppressWarnings("PublicField")
public class GitHubUserResolutionUi {
    /** Explicitly configured GitHub logins. */
    public List<String> configuredLogins = new ArrayList<>();

    /** GitHub logins matched by email among cached PR authors. */
    public List<String> autoResolvedLogins = new ArrayList<>();

    /** All logins usable for filtering current user's PRs. */
    public List<String> allLogins = new ArrayList<>();

    /** */
    public GitHubUserResolutionUi() {
        // No-op.
    }

    /**
     * @param resolution Resolution.
     */
    public GitHubUserResolutionUi(GitHubUserResolver.Resolution resolution) {
        configuredLogins.addAll(resolution.configuredLogins);
        autoResolvedLogins.addAll(resolution.autoResolvedLogins);
        allLogins.addAll(resolution.allLogins());
    }
}
