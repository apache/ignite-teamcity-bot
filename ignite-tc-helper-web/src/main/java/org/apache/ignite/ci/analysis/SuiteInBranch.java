package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;

/**
 * Created by dpavlov on 09.08.2017.
 */
@Persisted
public class SuiteInBranch implements Comparable<SuiteInBranch> {
    public String id;
    public String branch;

    public SuiteInBranch(String id, String branch) {
        this.id = id;
        this.branch = branch;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        SuiteInBranch id = (SuiteInBranch)o;

        if (this.id != null ? !this.id.equals(id.id) : id.id != null)
            return false;
        return branch != null ? branch.equals(id.branch) : id.branch == null;
    }

    @Override public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (branch != null ? branch.hashCode() : 0);
        return result;
    }

    @Override public int compareTo(SuiteInBranch o) {
        int runConfCompare = id.compareTo(o.id);
        if (runConfCompare != 0)
            return runConfCompare;
        return branch.compareTo(o.branch);
    }

    @Override public String toString() {
        return "{" +
            "id='" + id + '\'' +
            ", branch='" + branch + '\'' +
            '}';
    }

    public String getBranch() {
        return branch;
    }

    public String getSuiteId() {
        return id;
    }
}
