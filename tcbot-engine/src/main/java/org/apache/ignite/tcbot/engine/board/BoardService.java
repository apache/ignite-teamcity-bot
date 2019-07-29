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
package org.apache.ignite.tcbot.engine.board;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.ignite.ci.issue.ChangeUi;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.ui.BoardDefectSummaryUi;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

public class BoardService {
    @Inject IIssuesStorage storage;

    public BoardSummaryUi summary(ICredentialsProv creds) {
        Stream<Issue> stream = storage.allIssues();

        BoardSummaryUi res = new BoardSummaryUi();
        long minIssueTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        stream.filter(issue -> {
            long detected = issue.detectedTs == null ? 0 : issue.detectedTs;

            return detected >= minIssueTs;
        }).forEach(issue -> {
            Set<String> collect = issue.changes.stream().map(ChangeUi::username).collect(Collectors.toSet());

            BoardDefectSummaryUi defect = new BoardDefectSummaryUi();

            defect.usernames = collect.toString();
            res.addDefect(defect);
        });

        return res;
    }
}
