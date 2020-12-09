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

package org.apache.ignite.tcbot.engine.issue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;

public interface IIssuesStorage {
    /**
     * Determines if the storage contains an entry for the specified issue key.
     *
     * @param issueKey Issue to be checked.
     * @return true if issue
     */
    public boolean containsIssueKey(IssueKey issueKey);

    public void saveIssue(Issue issue);

    public Issue getIssue(IssueKey issueKey);

    public Stream<Issue> allIssues();

    /**
     * Checks and saves address was notified (NotThreadSafe)
     * @param key issue key.
     * @param addr Address to register as notified.
     * @param e Exception. Null means notification was successfull. Non null means notification failed
     * @return update successful. This address was not notified before.
     */
    public boolean getIsNewAndSetNotified(IssueKey key, String addr, @Nullable Exception e);

    public void saveIssueSubscribersStat(IssueKey key, int cntSrvAllowed, int cntSubscribed, int cntTagsFilterPassed);

    public void removeOldIssues(Map<Integer, List<Integer>> oldBuildsTeamCityAndBuildIds);

    public void removeOldIssues(long thresholdDate, int numOfItemsToDel);
}
