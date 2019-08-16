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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.tcbot.engine.board.IssueResolveStatus;
import org.apache.ignite.tcbot.engine.defect.BlameCandidate;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public class BoardDefectSummaryUi {
    private final transient DefectCompacted defect;
    private final transient IStringCompactor compactor;

    public String branch;

    private Set<String> tags = new HashSet<>();

    private List<BoardDefectIssueUi> issuesList = new ArrayList<>();
    private boolean forceResolveAllowed;

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

    private static <X> String limitedListPrint(Collection<X> suites, Function<X, String> elemToStr) {
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

        return limitedListPrint(blameCandidates, next -> {
            String fullName = next.fullDisplayName(compactor);
            if (fullName != null)
                return fullName;

            // default, returning VCS username used
            String strVcsUsername = next.vcsUsername(compactor);

            if (!Strings.isNullOrEmpty(strVcsUsername) &&
                    strVcsUsername.contains("<") && strVcsUsername.contains(">")) {
                int emailStartIdx = strVcsUsername.indexOf('<');
                return strVcsUsername.substring(0, emailStartIdx).trim();
            }

            return strVcsUsername;
        });
    }

    public String getTrackedBranch() {
       return compactor.getStringFromId(defect.trackedBranchCid());
    }

    public int getId() {
        return defect.id();
    }

    public void addIssue(BoardDefectIssueUi issue) {
        issuesList.add(issue);
    }

    public List<BoardDefectIssueUi> getAllIssues() {
        return Collections.unmodifiableList(issuesList);
    }

    public int getCntIssues() {
        return issuesList.size();
    }

    private Stream<BoardDefectIssueUi> issues(IssueResolveStatus type) {
        return issuesList.stream().filter(iss -> iss.status() == type);
    }

    public List<BoardDefectIssueUi> getIgnoredIssues() {
        return issues(IssueResolveStatus.IGNORED).collect(Collectors.toList());
    }

    public String getSummaryIgnoredIssues() {
        return limitedListPrint(getIgnoredIssues(), BoardDefectIssueUi::getName);
    }

    public Integer getCntIgnoredIssues() {
        return (int) issues(IssueResolveStatus.IGNORED).count();
    }

    public List<BoardDefectIssueUi> getFailingIssues() {
        return issues(IssueResolveStatus.FAILING).collect(Collectors.toList());
    }

    public String getSummaryFailingIssues() {
        return limitedListPrint(getFailingIssues(), BoardDefectIssueUi::getName);
    }

    public Integer getCntFailingIssues() {
        return (int) issues(IssueResolveStatus.FAILING).count();
    }

    public boolean getForceResolveAllowed() {
        return forceResolveAllowed;
    }


    public List<BoardDefectIssueUi> getFixedIssues() {
        return issues(IssueResolveStatus.FIXED).collect(Collectors.toList());
    }

    public String getSummaryFixedIssues() {
        return limitedListPrint(getFixedIssues(), BoardDefectIssueUi::getName);
    }

    public Integer getCntFixedIssues() {
        return (int) issues(IssueResolveStatus.FIXED).count();
    }

    public List<BoardDefectIssueUi> getUnclearIssues() {
        return issues(IssueResolveStatus.UNKNOWN).collect(Collectors.toList());
    }

    public String getSummaryUnclearIssues() {
        return limitedListPrint(getUnclearIssues(), BoardDefectIssueUi::getName);
    }

    public Integer getCntUnclearIssues() {
        return (int) issues(IssueResolveStatus.UNKNOWN).count();
    }

    public void addTags(Set<String> parameters) {
        this.tags.addAll(parameters);
    }

    public void setForceResolveAllowed(boolean admin) {
        this.forceResolveAllowed = admin;
    }
}
