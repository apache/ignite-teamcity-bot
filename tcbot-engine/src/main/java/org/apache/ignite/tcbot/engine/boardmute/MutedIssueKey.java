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

package org.apache.ignite.tcbot.engine.boardmute;

import com.google.common.base.Strings;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;

public class MutedIssueKey {
    private int tcSrvId;

    private int nameId;

    private int branchId;

    private IssueType issueType;

    public MutedIssueKey(int tcSrvId, int nameId, int branchId, IssueType issueType) {
        this.tcSrvId = tcSrvId;
        this.nameId = nameId;
        this.branchId = branchId;
        this.issueType = issueType;
    }

    public int getTcSrvId() {
        return tcSrvId;
    }

    public void setTcSrvId(int tcSrvId) {
        this.tcSrvId = tcSrvId;
    }

    public int getNameId() {
        return nameId;
    }

    public void setNameId(int nameId) {
        this.nameId = nameId;
    }

    public int branchNameId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public IssueType getIssueType() {
        return issueType;
    }

    public void setIssueType(IssueType issueType) {
        this.issueType = issueType;
    }

    public static String parseName(String name) {
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
}
