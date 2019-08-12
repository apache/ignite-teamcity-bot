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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ignite.tcbot.engine.defect.BlameCandidate;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public class BoardDefectSummaryUi {
    private final transient DefectCompacted defect;
    private final transient IStringCompactor compactor;

    public String branch;

    public Integer cntIssues;
    public Integer fixedIssues;
    public Integer notFixedIssues;
    public Integer unclearIssues;

    public List<String> testOrSuitesAffected = new ArrayList<>();
    private Set<String> tags = new HashSet<>();

    public BoardDefectSummaryUi(DefectCompacted defect, IStringCompactor compactor) {
        this.defect = defect;
        this.compactor = compactor;
    }

    public Set<String> getTags() {
        //todo bad code, make tag filter configurable.
        return tags.stream().filter(t -> t.length() <= 2).collect(Collectors.toSet());
    }

    public List<String> getSuites() {
        return defect.buildsInvolved().values().stream().map(
            b -> b.build().buildTypeName()
        ).distinct().map(compactor::getStringFromId).collect(Collectors.toList());
    }

    public String getSuitesSummary() {
        List<String> suites = getSuites();

        return limitedListPrint(suites, t -> t);
    }

    public <X> String limitedListPrint(List<X> suites, Function<X, String> elemToStr) {
        StringBuilder res = new StringBuilder();
        int i = 0;
        for (X next : suites) {
            if (i >= 3) {
                int rest = suites.size() - i;

                res.append(" ... and ").append(rest).append(" more");
                break;
            }
            if (res.length() > 0)
                res.append(", ");

            res.append(elemToStr.apply(next));
            i++;
        }

        return res.toString();
    }

    public List<String> getBlameCandidates() {
        return defect.blameCandidates().stream().map(c -> c.vcsUsername(compactor)).collect(Collectors.toList());
    }

    public String getBlameCandidateSummary() {
        List<BlameCandidate> blameCandidates = defect.blameCandidates();

        if (blameCandidates.isEmpty())
            return "";

        return limitedListPrint(blameCandidates, next -> next.vcsUsername(compactor));
    }

    public String getTrackedBranch() {
       return compactor.getStringFromId(defect.trackedBranchCid());
    }

    public int getId() {
        return defect.id();
    }

    public void addIssue(String testOrBuildName, String trackedBranchName) {
        if (cntIssues == null)
            cntIssues = 0;

        cntIssues++;

        testOrSuitesAffected.add(testOrBuildName);

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

    public void addTags(Set<String> parameters) {
        this.tags.addAll(parameters);
    }

    public void addUnclearIssue() {
        if (unclearIssues == null)
            unclearIssues = 0;

        unclearIssues++;
    }
}
