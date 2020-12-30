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

package org.apache.ignite.tcbot.engine.boardmute;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

/**
 *
 */
public class MutedIssuesDao {
    /** Cache name. */
    private static final String BOARD_MUTE_CACHE_NAME = "mutedIssues";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** */
    public IgniteCache<MutedIssueKey, MutedIssueInfo> cache() {
        return igniteProvider.get().getOrCreateCache(CacheConfigs.getCache8PartsConfig(BOARD_MUTE_CACHE_NAME));
    }

    public void putIssue(MutedIssueKey issueKey, MutedIssueInfo issueInfo) {
        cache().put(issueKey, issueInfo);
    }

    public void removeIssue(MutedIssueKey issueKey) {
        cache().remove(issueKey);
    }

    public MutedIssueInfo getMutedIssue(MutedIssueKey issueKey) {
        return cache().get(issueKey);
    }

    public Map<MutedIssueKey, MutedIssueInfo> getAllMutedIssues() {
        Map<MutedIssueKey, MutedIssueInfo> issues = new HashMap<>();
        cache().forEach(e -> issues.put(e.getKey(), e.getValue()));
        return issues;
    }
}
