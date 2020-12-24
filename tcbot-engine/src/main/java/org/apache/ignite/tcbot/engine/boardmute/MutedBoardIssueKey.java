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

import org.apache.ignite.tcbot.engine.issue.IssueType;

public class MutedBoardIssueKey {
    private int tcSrvId;

    private String name;

    private int branchId;

    private IssueType issueType;

    public MutedBoardIssueKey(int tcSrvId, String name, int branchId, IssueType issueType) {
        this.tcSrvId = tcSrvId;
        this.name = name;
        this.branchId = branchId;
        this.issueType = issueType;
    }

    public int getTcSrvId() {
        return tcSrvId;
    }

    public void setTcSrvId(int tcSrvId) {
        this.tcSrvId = tcSrvId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
