package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;

/**
 * Created by Дмитрий on 17.02.2018.
 */
@Persisted
public class LogCheckResult implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;

    private int version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;
    private Integer threadDumpFileIdx;

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }

    public void setThreadDumpFileIdx(Integer threadDumpFileIdx) {
        this.threadDumpFileIdx = threadDumpFileIdx;
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
}
