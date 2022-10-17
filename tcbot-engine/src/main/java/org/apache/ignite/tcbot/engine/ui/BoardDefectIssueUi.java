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

import com.google.common.base.Strings;
import org.apache.ignite.tcbot.engine.board.IssueResolveStatus;
import org.apache.ignite.tcbot.engine.defect.DefectIssue;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import javax.annotation.Nullable;

/**
 * UI version for displaying org.apache.ignite.tcbot.engine.defect.DefectIssue and its current status
 */
@SuppressWarnings("unused")
public class BoardDefectIssueUi {
    private transient IStringCompactor compactor;
    private transient DefectIssue issue;
    private boolean suiteProblem;
    private int tcSrvId;
    @Nullable
    private String webUrl;
    private IssueResolveStatus status;

    public BoardDefectIssueUi(IssueResolveStatus status, IStringCompactor compactor,
                              DefectIssue issue, boolean suiteProblem,
                              @Nullable String webUrl) {
        this.status = status;
        this.compactor = compactor;
        this.issue = issue;
        this.suiteProblem = suiteProblem;
        this.webUrl = webUrl;
    }

    public String getName() {
        String name = compactor.getStringFromId(issue.testNameCid());

        if(suiteProblem)
            return name;

        String suiteName = null, testName = null;

        String[] split = Strings.nullToEmpty(name).split("\\:");
        if (split.length >= 2) {
            suiteName = ShortTestFailureUi.extractSuite(split[0]);
            testName = ShortTestFailureUi.extractTest(split[1]);
        }

        if (testName != null && suiteName != null)
            return suiteName + ":" + testName;

        return name;

    }

    public int getNameId() {
        return issue.testNameCid();
    }

    public int getIssueTypeCode() {
        return issue.issueTypeCode();
    }

    public String getIssueType() {
        return compactor.getStringFromId(issue.issueTypeCode());
    }

    public String getStatus() {
        return status.toString();
    }

    public IssueResolveStatus status() {
        return status;
    }

    public void setStatus(IssueResolveStatus status) {
        this.status = status;
    }

    @Nullable
    public String getWebUrl() {
        return webUrl;
    }

    public int getTcSrvId() {
        return tcSrvId;
    }

    public void setTcSrvId(int tcSrvId) {
        this.tcSrvId = tcSrvId;
    }
}
