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
package org.apache.ignite.tcbot.engine.defect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;

public class DefectCompacted {
    /** Synthetic Defect Id. */
    private int id;

    private int tcBranch = -1;

    /** Tc server code hashcode. */
    private int tcSrvId = -1;

    /** Tc server code compactor string ID. */
    private int tcSrvCodeCid = -1;

    /** Tracked branch Compactor string ID. */
    private int trackedBranchCid = -1;

    /** Resolved by username id : Compactor ID of user login (principal ID). */
    private int resolvedByUsernameId = -1;

    /** Resolved timestamp. */
    private long resolvedTs = -1;

    /** Commits hashes involved. */
    private List<CommitCompacted> commits = new ArrayList<>();

    /** Blame candidates. */
    private List<BlameCandidate> blameCandidates = new ArrayList<>();

    private Map<Integer, DefectFirstBuild> buildsInvolved = new HashMap<>();
    private Map<Integer, ChangeCompacted> changes = new HashMap<>();

    public DefectCompacted(int id) {
        this.id = id;
    }

    public int resolvedByUsernameId() {
        return resolvedByUsernameId;
    }

    /**
     * @param collect Collected commits, should be sorted.
     */
    public boolean sameCommits(List<CommitCompacted> collect) {
        return commits.equals(collect);
    }

    /**
     * @param collect Collected commits, should be sorted.
     */
    public DefectCompacted commits(List<CommitCompacted> collect) {
        commits.clear();
        commits.addAll(collect);

        return this;
    }

    public Map<Integer, DefectFirstBuild> buildsInvolved() {
        return Collections.unmodifiableMap(buildsInvolved);
    }

    public DefectFirstBuild computeIfAbsent(FatBuildCompacted build) {
        return buildsInvolved.computeIfAbsent(build.id(), k -> new DefectFirstBuild(build));
    }

    public int tcSrvId() {
        return tcSrvId;
    }

    public void trackedBranchCidSetIfEmpty(int trackedBranchCid) {
        if (this.trackedBranchCid <= 0)
            this.trackedBranchCid = trackedBranchCid;

    }

    /** */
    public String tcBranch(IStringCompactor compactor) {
        return compactor.getStringFromId(tcBranch);
    }

    /** */
    public String tcSrvCode(IStringCompactor compactor) {
        return compactor.getStringFromId(tcSrvCodeCid);
    }

    /** */
    public int id() {
        return id;
    }

    /** */
    public DefectCompacted tcBranch(int tcBranch) {
        this.tcBranch = tcBranch;

        return this;
    }

    /** */
    public DefectCompacted tcSrvId(int srvId) {
        this.tcSrvId = srvId;
        return this;
    }

    /** */
    public DefectCompacted tcSrvCodeCid(int cid) {
        tcSrvCodeCid = cid;

        return this;
    }

    public void id(int id) {
        this.id = id;
    }

    public boolean hasBuild(int id) {
        return buildsInvolved.containsKey(id);
    }

    public List<BlameCandidate> blameCandidates() {
        if (blameCandidates == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(blameCandidates);
    }

    public DefectCompacted changeMap(Map<Integer, ChangeCompacted> changes) {
        this.changes = changes;

        return this;
    }

    public Map<Integer, ChangeCompacted> changeMap() {
        return changes;
    }

    public void addBlameCandidate(BlameCandidate candidate) {
        blameCandidates.add(candidate);
    }

    public int trackedBranchCid() {
        return trackedBranchCid;
    }

    public void resolvedByUsernameId(int stringId) {
        this.resolvedByUsernameId = stringId;
        this.resolvedTs = System.currentTimeMillis();
    }

    public void removeOldVerBlameCandidates() {
        blameCandidates.removeIf(IVersionedEntity::isOutdatedEntityVersion);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DefectCompacted compacted = (DefectCompacted)o;
        return id == compacted.id &&
            tcBranch == compacted.tcBranch &&
            tcSrvId == compacted.tcSrvId &&
            tcSrvCodeCid == compacted.tcSrvCodeCid &&
            trackedBranchCid == compacted.trackedBranchCid &&
            resolvedByUsernameId == compacted.resolvedByUsernameId &&
            resolvedTs == compacted.resolvedTs &&
            Objects.equals(commits, compacted.commits) &&
            Objects.equals(blameCandidates, compacted.blameCandidates) &&
            Objects.equals(buildsInvolved, compacted.buildsInvolved) &&
            Objects.equals(changes, compacted.changes);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(id, tcBranch, tcSrvId, tcSrvCodeCid, trackedBranchCid, resolvedByUsernameId, resolvedTs,
            commits, blameCandidates, buildsInvolved, changes);
    }
}
