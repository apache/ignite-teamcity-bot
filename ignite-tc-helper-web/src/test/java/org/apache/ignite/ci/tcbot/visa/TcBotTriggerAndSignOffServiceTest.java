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
import org.apache.ignite.ci.github.GitHubIssueComment;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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
    }

    /**
     * Checks partial status when JIRA was commented but GitHub was not.
     */
    @Test public void bothTargetsReturnPartialStatusWhenGitHubFailsAfterJiraSuccess() {
        Visa partial = TcBotTriggerAndSignOffService.commentResult("JIRA,GITHUB", false,
            new JiraCommentResponse(), 2);

        assertEquals(Visa.PARTIALLY_COMMENTED, partial.status);
        assertTrue(partial.isSuccess());
        assertEquals(2, partial.getBlockers());
    }

    /**
     * Checks duplicate detection by marker or build URL.
     */
    @Test public void duplicateDetectionIsDefensiveAndChecksMarkerOrBuildUrl() {
        TcBotTriggerAndSignOffService svc = new TcBotTriggerAndSignOffService();
        IGitHubConnIgnited gh = mock(IGitHubConnIgnited.class);

        when(gh.getIssueComments(42)).thenReturn(null);

        assertFalse(svc.hasExistingGitHubComment(gh, 42, "marker", "build-url"));

        GitHubIssueComment markerComment = mock(GitHubIssueComment.class);

        when(markerComment.body()).thenReturn("existing marker comment");
        when(gh.getIssueComments(42)).thenReturn(Arrays.asList(markerComment));

        assertTrue(svc.hasExistingGitHubComment(gh, 42, "marker", "build-url"));

        GitHubIssueComment buildUrlComment = mock(GitHubIssueComment.class);

        when(buildUrlComment.body()).thenReturn("existing build-url comment");
        when(gh.getIssueComments(42)).thenReturn(Arrays.asList(buildUrlComment));

        assertTrue(svc.hasExistingGitHubComment(gh, 42, "marker", "build-url"));
    }
}
