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
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
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
        muteCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(BOARD_MUTE_CACHE_NAME));
//        muteCache.removeAll();
    }

    public void muteTest(int defectId, String branch, String issueName, String jiraTicket, String comment, String userName) {
        MutedBoardDefect mutedBoardDefect = muteCache.get(defectId);

        if (mutedBoardDefect == null)
            mutedBoardDefect = new MutedBoardDefect(defectId, branch);

        mutedBoardDefect.getMutedIssues().add(new MutedBoardIssue(issueName, jiraTicket, comment, userName, ZonedDateTime.now()));

        muteCache.put(defectId, mutedBoardDefect);
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
            return "UNKNOWN";
        else if (mutedDefect.isTestMuted(issueName))
            return mutedDefect.userName(issueName);
        else return "UNKNOWN";
    }
}
