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

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

/**
 *
 */
public class MuteBoardDao {
    /** Cache name. */
    private static final String BOARD_MUTE_CACHE_NAME = "boardMute";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<MutedBoardIssueKey, MutedBoardIssueInfo> muteCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        if (muteCache == null)
            igniteProvider.get().destroyCache(BOARD_MUTE_CACHE_NAME);

        muteCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(BOARD_MUTE_CACHE_NAME));
//        muteCache.removeAll();
//        muteCache.clear();
    }

    public void putIssue(MutedBoardIssueKey issueKey, MutedBoardIssueInfo issueInfo) {
        muteCache.put(issueKey, issueInfo);
    }

    public void removeIssue(MutedBoardIssueKey issueKey) {
        muteCache.remove(issueKey);
    }

    public MutedBoardIssueInfo getMutedBoardDefect(MutedBoardIssueKey issueKey) {
        return muteCache.get(issueKey);
    }

    public Map<MutedBoardIssueKey, MutedBoardIssueInfo> getAllMutedIssues() {
        Map<MutedBoardIssueKey, MutedBoardIssueInfo> issues = new HashMap<>();
        muteCache.forEach(e -> issues.put(e.getKey(), e.getValue()));
        return issues;
    }
}
