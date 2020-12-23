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

import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.tcbot.engine.issue.IssueType;

public class MutedBoardIssueKey {
    private int tcSrvId = -1;

    /** Test name */
    private String name;

    /** Compacter identifier for string 'Branch name'. */
    @QuerySqlField(index = true)
    private int branchNameId = -1;

    private IssueType issueType;

    public MutedBoardIssueKey(int tcSrvId, String name, int branchNameId, IssueType issueType) {
        this.tcSrvId = tcSrvId;
        this.name = name;
        this.branchNameId = branchNameId;
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
        return branchNameId;
    }

    public void setBranchNameId(int branchNameId) {
        this.branchNameId = branchNameId;
    }

    public IssueType getIssueType() {
        return issueType;
    }

    public void setIssueType(IssueType issueType) {
        this.issueType = issueType;
    }
}
