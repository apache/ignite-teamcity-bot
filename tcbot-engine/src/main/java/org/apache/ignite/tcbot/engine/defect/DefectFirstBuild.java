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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;

public class DefectFirstBuild {
    private BuildRefCompacted build;

    private Set<DefectIssue> issues = new HashSet<>();

    public DefectFirstBuild(BuildRefCompacted build) {
        this.build = build;
    }

    public DefectFirstBuild addIssue(int typeCid, Integer testNameCid) {
        issues.add(new DefectIssue(typeCid, testNameCid));

        return this;
    }

    public BuildRefCompacted build() {
        return build;
    }

    public Set<DefectIssue> issues() {
        return Collections.unmodifiableSet(issues);
    }
}
