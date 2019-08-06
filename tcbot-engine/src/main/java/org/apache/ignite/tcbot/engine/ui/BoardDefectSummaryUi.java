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

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public class BoardDefectSummaryUi {
    private final transient DefectCompacted defect;
    private final transient IStringCompactor compactor;

    public String branch;

    public Integer cntIssues;
    public Integer fixedIssues;
    public Integer notFixedIssues;

    public String usernames = "";

    public String trackedBranch;
    public List<String> testOrSuitesAffected = new ArrayList<>();

    public BoardDefectSummaryUi(DefectCompacted defect, IStringCompactor compactor) {
        this.defect = defect;
        this.compactor = compactor;
    }

    public int getId(){
        return defect.id();
    }

    public void addIssue(String testOrBuildName, String trackedBranchName) {
        if (cntIssues == null)
            cntIssues = 0;

        cntIssues++;

        testOrSuitesAffected.add(testOrBuildName);

        trackedBranch = trackedBranchName;
    }

    public void addFixedIssue() {
        if (fixedIssues == null)
            fixedIssues = 0;

        fixedIssues++;
    }

    public void addNotFixedIssue() {
        if (notFixedIssues == null)
            notFixedIssues = 0;

        notFixedIssues++;
    }
}
