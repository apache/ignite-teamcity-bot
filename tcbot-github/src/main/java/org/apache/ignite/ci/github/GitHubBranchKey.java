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
package org.apache.ignite.ci.github;

import com.google.common.base.Strings;
import org.apache.ignite.tcbot.persistence.Persisted;

import javax.annotation.Nonnull;
import java.util.Objects;

@Persisted
public class GitHubBranchKey implements Comparable<GitHubBranchKey> {
    int srvId;
    String branchName;

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GitHubBranchKey key = (GitHubBranchKey)o;
        return srvId == key.srvId &&
            Objects.equals(branchName, key.branchName);
    }

    @Override public int hashCode() {
        return Objects.hash(srvId, branchName);
    }

    public GitHubBranchKey branchName(String name) {
        this.branchName = name;

        return this;
    }

    public GitHubBranchKey srvId(int high) {
        this.srvId = high;

        return this;
    }

    /** */
    public int srvId() {
        return srvId;
    }

    /** */
    public String branchName() {
        return branchName;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(@Nonnull GitHubBranchKey o) {
        int compare = Integer.compare(srvId, o.srvId());
        if (compare != 0)
            return compare;

        return Strings.nullToEmpty(branchName).compareTo(o.branchName());
    }
}
