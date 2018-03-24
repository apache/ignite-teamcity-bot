package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;

/**
 * Persistable Log check task result.
 */
@Persisted
public class LogCheckResult implements IVersionedEntity {
    private static final int LATEST_VERSION = 4;

    @SuppressWarnings("FieldCanBeLocal") private Integer _version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;

    private String lastThreadDump;

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
}
