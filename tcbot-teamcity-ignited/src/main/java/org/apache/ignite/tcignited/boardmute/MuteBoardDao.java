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

package org.apache.ignite.tcignited.boardmute;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
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
    private IgniteCache<Integer, MutedBoardDefect> muteCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
//        igniteProvider.get().destroyCache(BOARD_MUTE_CACHE_NAME);
        muteCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(BOARD_MUTE_CACHE_NAME));
//        muteCache.removeAll();
//        muteCache.clear();
    }

    public void muteTest(int defectId, String branch, String issueName, String jiraTicket, String comment, String userName, String webUrl) {
        MutedBoardDefect mutedBoardDefect = muteCache.get(defectId);

        if (mutedBoardDefect == null)
            mutedBoardDefect = new MutedBoardDefect(defectId, branch);

        mutedBoardDefect.getMutedIssues().put(issueName, new MutedBoardIssueInfo(jiraTicket, comment, userName, webUrl,ZonedDateTime.now()));

        muteCache.put(defectId, mutedBoardDefect);
    }

    public MutedBoardIssueInfo getMutedBoardDefect(int defectId, String issueName) {
        MutedBoardDefect mutedDefect = muteCache.get(defectId);
        if (mutedDefect != null)
            return mutedDefect.getMutedIssues().get(issueName);
        else
            return null;
    }

    public boolean isMuted(int defectId, String issueName) {
        MutedBoardDefect mutedDefect = muteCache.get(defectId);
        if (mutedDefect == null)
            return false;
        else if (mutedDefect.isTestMuted(issueName))
            return true;
        else return false;
    }

    public String mutedByUser(int defectId, String issueName) {
        MutedBoardDefect mutedDefect = muteCache.get(defectId);
        if (mutedDefect == null)
            return "";
        else if (mutedDefect.isTestMuted(issueName))
            return mutedDefect.userName(issueName);
        else return "";
    }

    public Collection<MutedBoardDefect> getDefects() {
        Map<Integer, MutedBoardDefect> mutedIssues = new TreeMap<>();
        try (QueryCursor<Cache.Entry<Integer, MutedBoardDefect>> qry = muteCache.query(new ScanQuery<>())) {
            for (Cache.Entry<Integer, MutedBoardDefect> next : qry) {
                mutedIssues.put(next.getKey(), next.getValue());
            }
        }
        return mutedIssues.values();
    }
}
