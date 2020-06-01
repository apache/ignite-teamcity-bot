package org.apache.ignite.tcbot.engine.conf;

public interface ICleanerConfig {
    /** */
    Integer getSafeDays();

    /** */
    Integer getNumOfItemsToDel();
}
