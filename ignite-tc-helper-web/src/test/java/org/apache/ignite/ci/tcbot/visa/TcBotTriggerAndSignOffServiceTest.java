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
import org.apache.ignite.ci.web.model.CompactVisa;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.jiraignited.IJiraIgnited;
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
     * Checks skipped run result comment status names blockers count.
     */
    @Test public void skippedRunResultCommentNamesBlockersCount() {
        Visa skipped = Visa.skipped(3);

        assertEquals("Run result comment skipped: 3 blockers found.", skipped.status);
        assertTrue(skipped.isSuccess());
        assertEquals(3, skipped.getBlockers());
    }

    /**
     * Checks machine-readable result is not inferred from user-visible status in new Visa instances.
     */
    @Test public void visaSuccessDoesNotDependOnStatusText() {
        JiraCommentResponse res = new JiraCommentResponse();

        assertFalse(Visa.failure("JIRA ticket commented: IGNITE-1 https://issues.example/IGNITE-1").isSuccess());
        assertTrue(Visa.success("GitHub wasn't commented - ignored text in explicit success", res, 0).isSuccess());
    }

    /**
     * Checks compact Visa stores machine-readable result.
     */
    @Test public void compactVisaStoresResultCode() {
        IStringCompactor compactor = mock(IStringCompactor.class);
        JiraCommentResponse res = new JiraCommentResponse();

        when(compactor.getStringId("Any visible text")).thenReturn(12);
        when(compactor.getStringFromId(12)).thenReturn("Any visible text");

        Visa restored = new CompactVisa(Visa.success("Any visible text", res, 1), compactor).toVisa(compactor);

        assertTrue(restored.isSuccess());
        assertEquals("Any visible text", restored.status);
        assertEquals(1, restored.blockers);
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

        Visa jiraDuplicate = TcBotTriggerAndSignOffService.commentResult(CommentTargets.JIRA, true, null, null,
            "JIRA ticket already has a valid TCBot comment for this build: IGNITE-28641 " +
                "https://issues.apache.org/jira/browse/IGNITE-28641",
            null, 0);

        assertTrue(jiraDuplicate.status.contains("already has a valid TCBot comment"));
        assertTrue(jiraDuplicate.isSuccess());
    }

    /**
     * Checks duplicate marker includes only rerun build ids, not every analyzed suite build id.
     */
    @Test public void analysisSliceKeyIncludesOnlyRerunBuildIds() {
        assertEquals("chainBuildId=100 rerunBuildIds=200,300",
            TcBotTriggerAndSignOffService.analysisSliceKey(100, Arrays.asList(300, 100, 200)));

        assertEquals("chainBuildId=100 rerunBuildIds=none",
            TcBotTriggerAndSignOffService.analysisSliceKey(100, null));
    }

    /**
     * Checks JIRA duplicate detection by analysis marker.
     */
    @Test public void jiraDuplicateDetectionChecksMarker() throws Exception {
        TcBotTriggerAndSignOffService svc = new TcBotTriggerAndSignOffService();
        IJiraIgnited jira = mock(IJiraIgnited.class);

        when(jira.getJiraComments("IGNITE-1")).thenReturn("{\"comments\":[{\"body\":\"" +
            "tcbot-analysis-comment chainBuildId=100 rerunBuildIds=200\"}]}");

        assertTrue(svc.hasExistingJiraComment(jira, "IGNITE-1",
            "tcbot-analysis-comment chainBuildId=100 rerunBuildIds=200"));
        assertFalse(svc.hasExistingJiraComment(jira, "IGNITE-1",
            "tcbot-analysis-comment chainBuildId=100 rerunBuildIds=300"));
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
