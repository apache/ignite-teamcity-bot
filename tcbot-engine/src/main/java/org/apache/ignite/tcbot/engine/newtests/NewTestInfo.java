package org.apache.ignite.tcbot.engine.newtests;

/** */
public class NewTestInfo {
    private String branch;

    /** */
    private long timestamp;

    /** */
    public NewTestInfo(String branch, long timestamp) {
        this.branch = branch;
        this.timestamp = timestamp;
    }

    /** */
    public String branch() {
        return branch;
    }

    /** */
    public long timestamp() {
        return timestamp;
    }
}
