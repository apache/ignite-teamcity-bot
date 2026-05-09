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

package org.apache.ignite.ci.tcbot.visa;

import java.util.Arrays;
import java.util.Collections;
import org.apache.ignite.ci.github.GitHubIssueComment;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.observer.BuildsInfo;
import org.apache.ignite.ci.observer.CompactBuildsInfo;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests comment target validation and GitHub/JIRA comment result handling.
 */
public class TcBotTriggerAndSignOffServiceTest {
    /**
     * Checks comment target normalization.
     */
    @Test public void commentTargetsNormalizeAllowsOnlyKnownTargets() {
        assertEquals(CommentTargets.JIRA, CommentTargets.normalize(null));
        assertEquals("JIRA,GITHUB", CommentTargets.normalize("jira, github, JIRA"));

        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
            () -> CommentTargets.normalize("FOO"));

        assertTrue(err.getMessage().contains("FOO"));
    }

    /**
     * Checks GitHub-only comment result statuses.
     */
    @Test public void githubOnlyResultReflectsSuccessAndFailure() {
        Visa success = TcBotTriggerAndSignOffService.commentResult(CommentTargets.GITHUB, true, null, 0);

        assertEquals(Visa.COMMENTED, success.status);
        assertTrue(success.isSuccess());

        Visa failure = TcBotTriggerAndSignOffService.commentResult(CommentTargets.GITHUB, false, null, 0);

        assertTrue(failure.status.contains("GitHub wasn't commented"));
        assertFalse(failure.isSuccess());

        Visa detailedFailure = TcBotTriggerAndSignOffService.commentResult(CommentTargets.GITHUB, false,
            "related PR was not found [prNum=42]", null, 0);

        assertTrue(detailedFailure.status.contains("related PR was not found [prNum=42]"));
        assertFalse(detailedFailure.isSuccess());
    }

    /**
     * Checks partial status when JIRA was commented but GitHub was not.
     */
    @Test public void bothTargetsReturnPartialStatusWhenGitHubFailsAfterJiraSuccess() {
        Visa partial = TcBotTriggerAndSignOffService.commentResult("JIRA,GITHUB", false,
            new JiraCommentResponse(), 2);

        assertTrue(partial.status.startsWith(Visa.PARTIALLY_COMMENTED));
        assertTrue(partial.status.contains("GitHub wasn't commented"));
        assertTrue(partial.isSuccess());
        assertEquals(2, partial.getBlockers());
    }

    /**
     * Checks duplicate detection by marker.
     */
    @Test public void duplicateDetectionIsDefensiveAndChecksMarker() {
        TcBotTriggerAndSignOffService svc = new TcBotTriggerAndSignOffService();
        IGitHubConnIgnited gh = mock(IGitHubConnIgnited.class);

        when(gh.getIssueComments(42)).thenReturn(null);

        assertFalse(svc.hasExistingGitHubComment(gh, 42, "marker"));

        GitHubIssueComment markerComment = mock(GitHubIssueComment.class);

        when(markerComment.body()).thenReturn("existing marker comment");
        when(gh.getIssueComments(42)).thenReturn(Arrays.asList(markerComment));

        assertTrue(svc.hasExistingGitHubComment(gh, 42, "marker"));

        GitHubIssueComment buildUrlComment = mock(GitHubIssueComment.class);

        when(buildUrlComment.body()).thenReturn("existing build-url comment");
        when(gh.getIssueComments(42)).thenReturn(Arrays.asList(buildUrlComment));

        assertFalse(svc.hasExistingGitHubComment(gh, 42, "marker"));
    }

    /**
     * Checks detailed successful comment result statuses.
     */
    @Test public void detailedCommentResultNamesCommentedTargets() {
        Visa github = TcBotTriggerAndSignOffService.commentResult(CommentTargets.GITHUB, true, null,
            "GitHub PR already has a valid TCBot comment for this build: PR #13114 https://github.com/apache/ignite/pull/13114",
            null, null, 0);

        assertTrue(github.status.contains("already has a valid TCBot comment"));
        assertTrue(github.status.contains("https://github.com/apache/ignite/pull/13114"));
        assertTrue(github.isSuccess());

        Visa jira = TcBotTriggerAndSignOffService.commentResult(CommentTargets.JIRA, true, null, null,
            "JIRA ticket commented: IGNITE-28641 https://issues.apache.org/jira/browse/IGNITE-28641",
            new JiraCommentResponse(), 0);

        assertTrue(jira.status.contains("JIRA ticket commented: IGNITE-28641"));
        assertTrue(jira.isSuccess());
    }

    /**
     * Checks GitHub duplicate marker includes rerun suite build ids, not only the main chain id.
     */
    @Test public void analysisSliceKeyIncludesSuiteBuildIds() {
        org.apache.ignite.tcbot.engine.ui.ShortSuiteUi blockers =
            new org.apache.ignite.tcbot.engine.ui.ShortSuiteUi();
        org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi newTests =
            new org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi();

        blockers.webToBuild = "https://ci.example/viewLog.html?buildId=200&buildTypeId=SuiteA";
        newTests.webToBuild = "https://ci.example/viewLog.html?buildId=300&buildTypeId=SuiteB";

        assertEquals("chainBuildId=100 suiteBuildIds=200,300",
            TcBotTriggerAndSignOffService.analysisSliceKey(100,
                Collections.singletonList(blockers), Collections.singletonList(newTests)));
    }

    /**
     * Checks explicit PR lookup refreshes stale GitHub cache before reporting miss.
     */
    @Test public void findPullRequestByNumberRefreshesCacheOnMiss() {
        TcBotTriggerAndSignOffService svc = new TcBotTriggerAndSignOffService();
        IGitHubConnIgnited gh = mock(IGitHubConnIgnited.class);
        PullRequest pr = mock(PullRequest.class);

        when(pr.getNumber()).thenReturn(42);
        when(gh.getPullRequest(42)).thenReturn(null);
        when(gh.getPullRequests()).thenReturn(Collections.emptyList()).thenReturn(Collections.singletonList(pr));
        when(gh.refreshPullRequests()).thenReturn("refreshed");

        assertSame(pr, svc.findPullRequestByNumber(gh, 42));

        verify(gh).refreshPullRequests();
    }

    /**
     * Checks tested commit link points to the commit inside PR context.
     */
    @Test public void commitHtmlUrlUsesPullRequestCommitView() {
        TcBotTriggerAndSignOffService svc = new TcBotTriggerAndSignOffService();

        assertEquals("https://github.com/apache/ignite/pull/13114/commits/abcdef",
            svc.commitHtmlUrl("https://api.github.com/repos/apache/ignite", "abcdef", 13114));
    }

    /**
     * Checks old compacted entries with missing comment target id are restored without compactor lookup for {@code -1}.
     */
    @Test public void compactBuildsInfoOldCommentTargetsDoNotLookupNegativeStringId() {
        CompactBuildsInfo compact = new CompactBuildsInfo();
        IStringCompactor compactor = mock(IStringCompactor.class);

        when(compactor.getStringFromId(0)).thenReturn("value");

        BuildsInfo info = new BuildsInfo(compact, compactor);

        assertEquals(CommentTargets.JIRA, info.commentTargets);
        verify(compactor, never()).getStringFromId(-1);
    }
}
