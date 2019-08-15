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
package org.apache.ignite.tcbot.engine.ui;

import org.apache.ignite.tcbot.engine.board.IssueResolveStatus;
import org.apache.ignite.tcbot.engine.defect.DefectIssue;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

/**
 * UI version for displaying org.apache.ignite.tcbot.engine.defect.DefectIssue and its current status
 */
public class BoardDefectIssueUi {
    private transient IStringCompactor compactor;
    private transient DefectIssue issue;
    private IssueResolveStatus status;

    public BoardDefectIssueUi(IssueResolveStatus status, IStringCompactor compactor,
        DefectIssue issue, int testNameCid, int code) {
        this.status = status;
        this.compactor = compactor;
        this.issue = issue;
    }

    public String getName() {
        return compactor.getStringFromId(issue.testNameCid());
    }

    public String getIssueType() {
        return compactor.getStringFromId(issue.issueTypeCode());
    }

    public String getStatus() {
        return status.toString();
    }
}
