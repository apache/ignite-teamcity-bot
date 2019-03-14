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

package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class TestInBranch implements Comparable<TestInBranch> {
    public String name;

    public String branch;

    public TestInBranch(String name, String branch) {
        this.name = name;
        this.branch = branch;
    }

    /** {@inheritDoc} */
    @Override public int compareTo(TestInBranch o) {
        int runConfCompare = name.compareTo(o.name);
        if (runConfCompare != 0)
            return runConfCompare;
        return branch.compareTo(o.branch);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        TestInBranch branch1 = (TestInBranch)o;

        return Objects.equals(name, branch1.name) &&
            Objects.equals(branch, branch1.branch);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(name, branch);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("branch", branch)
            .toString();
    }

    public String getName() {
        return name;
    }
}
