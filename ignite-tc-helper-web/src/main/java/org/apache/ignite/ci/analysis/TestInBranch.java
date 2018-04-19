package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.apache.ignite.ci.db.Persisted;

@Persisted
public class TestInBranch implements Comparable<TestInBranch> {
    public String name;

    public String branch;

    public TestInBranch(String name, String branch) {
        this.name = name;
        this.branch = branch;
    }

    @Override public int compareTo(TestInBranch o) {
        int runConfCompare = name.compareTo(o.name);
        if (runConfCompare != 0)
            return runConfCompare;
        return branch.compareTo(o.branch);
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestInBranch branch1 = (TestInBranch)o;
        return Objects.equal(name, branch1.name) &&
            Objects.equal(branch, branch1.branch);
    }

    @Override public int hashCode() {
        return Objects.hashCode(name, branch);
    }

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
