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

package org.apache.ignite.ci.runners;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.jiraignited.JiraTicketDao;
import org.apache.ignite.tcbot.engine.user.UserAndSessionsStorage;

/**
 * Utility class for local connection to TC helper DB (server) and any manipulations with data needed.
 */
public class ClientTmpHelper {
    public static void main(String[] args) {
        //select some main option here.
        //mainDropIssHist(args);

        mainSetUserAsAdmin(args);
    }

    /**
     * @param args Args.
     */
    public static void mainSetUserAsAdmin(String[] args) {
        try (Ignite ignite = TcHelperDb.startClient()) {
            IgniteCache<String, TcHelperUser> users = ignite.cache(UserAndSessionsStorage.USERS);
            TcHelperUser user = users.get("dpavlov");
            user.setAdmin(true);
            users.put(user.username, user);
        }
    }


    /**
     * @param args Args.
     */
    public static void mainConsistCheck(String[] args) {
        int inconsistent;
        try (Ignite ignite = TcHelperDb.startClient()) {
            RemoteClientTmpHelper.DUMPS = "dumpsLocal";

            inconsistent = RemoteClientTmpHelper.validateBuildIdConsistency(ignite);
        }

        System.err.println("Inconsistent builds in queue found [" +
            +inconsistent + "]");
    }

    /**
     * @param args Args.
     */
    public static void mainDropIssHist(String[] args) {
        Ignite ignite = TcHelperDb.startClient();

        IgniteCache<Object, Object> cache = ignite.cache(IssuesStorage.BOT_DETECTED_ISSUES);

        cache.forEach(
            issue -> {
                Object key = issue.getKey();
                Issue val = (Issue)issue.getValue();

                if(val.issueKey.testOrBuildName.contains(
                    "GridCachePartitionEvictionDuringReadThroughSelfTest.testPartitionRent"))
                    val.addressNotified.clear();

                cache.put(key, val);
            }
        );

        ignite.close();
    }

    public static void mainDeletePRs(String[] args) {
        Ignite ignite = TcHelperDb.startClient();

        IgniteCache<Object, Object> cache = ignite.cache(IGitHubConnIgnited.GIT_HUB_PR);

        cache.clear();
        ignite.close();
    }


    public static void mainDeleteTickets(String[] args) {
        Ignite ignite = TcHelperDb.startClient();

        IgniteCache<Object, Object> cache = ignite.cache(JiraTicketDao.TEAMCITY_JIRA_TICKET_CACHE_NAME);

        cache.clear();
        ignite.close();
    }
}
