package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;

/**
 * Created by Дмитрий on 17.02.2018
 */
@Persisted
public class LogCheckResult implements IVersionedEntity {
    public static final int LATEST_VERSION = 3;

    private int version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;
    @Deprecated
    private Integer threadDumpFileIdx;

    private String threadDump;

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }

    public void setThreadDump(String threadDump) {
        this.threadDump = threadDump;
    }

    public String getLastStartedTest() {
        return lastStartedTest;
    }

    @Override public int version() {
        return version;
    }

    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public String getThreadDump() {
        return threadDump;
    }
}
