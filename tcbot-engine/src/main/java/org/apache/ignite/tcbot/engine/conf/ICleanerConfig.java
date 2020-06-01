package org.apache.ignite.tcbot.engine.conf;

public interface ICleanerConfig {
    /** */
    int safeDays();

    /** */
    int numOfItemsToDel();

    /** */
    boolean enabled();
}
