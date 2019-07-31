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
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;

public class DefectCompacted {
    public int tcBranch;
    public int tcSrvId;
    public int tcSrvCodeCid;

    private int resolvedByUsernameId = -1;
    /** Commits hashes involved. */
    private List<CommitCompacted> commits = new ArrayList<>();
    private Map<Integer, DefectFirstBuild> buildsInvolved = new HashMap<>();

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
    public void commits(List<CommitCompacted> collect) {
        commits.clear();
        commits.addAll(collect);
    }

    public Map<Integer, DefectFirstBuild> buildsInvolved() {
        return Collections.unmodifiableMap(buildsInvolved);
    }

    public DefectFirstBuild computeIfAbsent(BuildRefCompacted build) {
        return buildsInvolved.computeIfAbsent(build.id(), k->{
            DefectFirstBuild defectFirstBuild = new DefectFirstBuild(build);
            return defectFirstBuild;
        });
    }
}
