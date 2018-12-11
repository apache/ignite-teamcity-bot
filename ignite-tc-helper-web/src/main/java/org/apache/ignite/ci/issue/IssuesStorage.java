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

package org.apache.ignite.ci.issue;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;

import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

import org.apache.ignite.ci.db.TcHelperDb;

public class IssuesStorage {
    public static final String BOT_DETECTED_ISSUES = "botDetectedIssues";

    @Inject
    private Provider<Ignite> igniteProvider;

    public IssuesStorage() {
    }

    public IgniteCache<IssueKey, Issue> cache() {
        return botDetectedIssuesCache(getIgnite());
    }

    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    public static IgniteCache<IssueKey, Issue> botDetectedIssuesCache(Ignite ignite) {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(BOT_DETECTED_ISSUES));
    }

    public List<Issue> all() {
        List<Issue> res = new ArrayList<>();

        for (Cache.Entry<IssueKey, Issue> next : cache()) {
            if (next.getValue().issueKey() == null)
                continue;

            res.add(next.getValue());
        }

        return res;
    }

    public boolean needNotify(IssueKey issueKey, String to) {
        Issue issue = cache().get(issueKey);
        if (issue == null)
            return false;

        boolean add = issue.addressNotified.add(to);

        if (add)
            cache().put(issueKey, issue);

        return add;
    }
}
