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

import org.apache.ignite.tcbot.persistence.Persisted;

import java.util.Objects;

@Persisted
public class GitHubBranch {
    /** Label. */
    private String label;

    /** Branch name. */
    private String ref;

    /** Sha of latest commit. */
    private String sha;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GitHubBranch branch = (GitHubBranch)o;
        return Objects.equals(label, branch.label) &&
            Objects.equals(ref, branch.ref) &&
            Objects.equals(sha, branch.sha);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(label, ref, sha);
    }

    public String ref() {
        return ref;
    }

    public String sha() {
        return sha;
    }
}
