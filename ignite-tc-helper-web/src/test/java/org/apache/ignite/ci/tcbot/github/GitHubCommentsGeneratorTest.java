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

package org.apache.ignite.ci.tcbot.github;

import java.util.Collections;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 *
 */
public class GitHubCommentsGeneratorTest {
    /** */
    @Test
    public void generateGitHubCommentIncludesNoBlockersAndDuplicateMarker() {
        String comment = GitHubCommentsGenerator.generateGitHubComment(
            mock(IStringCompactor.class),
            Collections.emptyList(),
            Collections.emptyList(),
            "https://ci.example/viewLog.html?buildId=42",
            "IgniteTests24Java8_RunAll",
            mock(ITeamcityIgnited.class),
            0,
            "pull/100/head",
            "<default>",
            "[abcdef1](https://github.com/apache/ignite/commit/abcdef123456)",
            42
        );

        assertTrue(comment.contains(GitHubCommentsGenerator.duplicateMarker(42)));
        assertTrue(comment.contains("No blockers found."));
        assertTrue(comment.contains("No new tests found."));
        assertTrue(comment.contains("[abcdef1](https://github.com/apache/ignite/commit/abcdef123456)"));
    }
}
