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

package org.apache.ignite.ci.conf;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pair of serverId and suiteId.
 */
@SuppressWarnings("PublicField")
public class ChainAtServer {
    /** Server ID to access config files within helper. */
    @Nullable public String serverId;

    /** Suite identifier by teamcity identification for root chain. */
    @Nonnull public String suiteId;

    /** URL for git integration. */
    @Nullable public String gitApiUrl;

    /** URL for JIRA integration. */
    @Nullable public String jiraApiUrl;

    public ChainAtServer() {

    }

    public ChainAtServer(ChainAtServer o) {
        this.serverId = o.serverId;
        this.suiteId = o.suiteId;
        this.gitApiUrl = o.gitApiUrl;
        this.jiraApiUrl = o.jiraApiUrl;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        ChainAtServer srv = (ChainAtServer)o;

        return Objects.equals(serverId, srv.serverId) &&
            Objects.equals(suiteId, srv.suiteId)&&
            Objects.equals(gitApiUrl, srv.gitApiUrl)&&
            Objects.equals(jiraApiUrl, srv.jiraApiUrl);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(serverId, suiteId, gitApiUrl, jiraApiUrl);
    }

    /**
     * @return Server ID to access config files within helper.
     */
    @Nullable public String getServerId() {
        return serverId;
    }
}
