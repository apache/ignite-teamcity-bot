package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Map;
import org.apache.ignite.ci.db.Persisted;

/**
 * Persistable Log check task result.
 */
@Persisted
public class LogCheckResult implements IVersionedEntity {
    private static final int LATEST_VERSION = 5;

    @SuppressWarnings("FieldCanBeLocal") private Integer _version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;

    private String lastThreadDump;

    private Map<String, List<String>> testWarns;

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }

    public void setLastThreadDump(String lastThreadDump) {
        this.lastThreadDump = lastThreadDump;
    }

    public String getLastStartedTest() {
        return lastStartedTest;
    }

    @Override public int version() {
        return _version == null ? -1 : _version;
    }

    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public String getLastThreadDump() {
        return lastThreadDump;
    }

    public void setTestWarns(Map<String, List<String>> testWarns) {
        this.testWarns = testWarns;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("lastStartedTest", lastStartedTest)
            .add("lastThreadDump", lastThreadDump)
            .add("testWarns", getWarns())
            .toString();
    }

    private String getWarns() {
        StringBuilder sb = new StringBuilder();

        testWarns.forEach(
            (t, list) -> {
                sb.append(t).append("   :\n");

                list.forEach(w -> {
                    sb.append(w).append("\n");

                });
            });
        return sb.toString();
    }

    public Map<String, List<String>> getTestWarns() {
        return testWarns;
    }
}
