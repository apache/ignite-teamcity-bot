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

import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcbot.issue.IIssuesStorage;

/**
 *
 */
public class IssuesStorage implements IIssuesStorage {
    public static final String BOT_DETECTED_ISSUES = "botDetectedIssues";

    @Inject
    private Provider<Ignite> igniteProvider;

    public IssuesStorage() {
    }

    private IgniteCache<IssueKey, Issue> cache() {
        return botDetectedIssuesCache(getIgnite());
    }

    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    public static IgniteCache<IssueKey, Issue> botDetectedIssuesCache(Ignite ignite) {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(BOT_DETECTED_ISSUES));
    }

    /** {@inheritDoc} */
    @Override public boolean getIsNewAndSetNotified(IssueKey issueKey, String to,
        @Nullable Exception e) {
        Issue issue = cache().get(issueKey);
        if (issue == null)
            return false;

        boolean update;
        if (e == null) {
            if (issue.notificationRetry >= 2 && issue.notificationFailed.containsKey(to))
                return false; // no more tries;

            update = issue.addressNotified.add(to);

            if (update && issue.notificationFailed != null)
                issue.notificationFailed.remove(to);
        }
        else {
            if (issue.notificationRetry < 2)
                issue.addressNotified.remove(to);

            issue.notificationRetry++;

            if (issue.notificationFailed == null)
                issue.notificationFailed = new HashMap<>();

            issue.notificationFailed.put(to, e.getClass().getSimpleName() + ": " + e.getMessage());

            update = true;
        }

        if (update)
            cache().put(issueKey, issue);

        return update;
    }

    @Override
    public void saveIssueSubscribersStat(IssueKey issueKey, int cntSrvAllowed, int cntSubscribed,
        int cntTagsFilterPassed) {
        Issue issue = cache().get(issueKey);
        if (issue == null)
            return;

        int hashCode = issue.hashCode();

        if (issue.stat == null)
            issue.stat = new HashMap<>();

        issue.stat.put("cntSrvAllowed", cntSrvAllowed);
        issue.stat.put("cntSubscribed", cntSubscribed);
        issue.stat.put("cntTagsFilterPassed", cntTagsFilterPassed);

        if (issue.hashCode() != hashCode)
            cache().put(issueKey, issue); // protect from odd writes
    }

    /** {@inheritDoc} */
    @Override public boolean containsIssueKey(IssueKey issueKey) {
        return cache().containsKey(issueKey);
    }

    /** {@inheritDoc} */
    @Override public void saveIssue(Issue issue) {
        cache().put(issue.issueKey(), issue);
    }

    /** {@inheritDoc} */
    @Override public Stream<Issue> allIssues() {
        return StreamSupport.stream(cache().spliterator(), false).map(Cache.Entry::getValue);
    }
}
