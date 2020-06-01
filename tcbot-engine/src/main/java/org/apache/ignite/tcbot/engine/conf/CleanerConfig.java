package org.apache.ignite.tcbot.engine.conf;

public class CleanerConfig implements ICleanerConfig{
    /** */
    public static final int DEFAULT_SAVE_DAYS = 30 * 6;

    /** */
    public static final int DEFAULT_NUMBER_OF_ITEMS_TO_DELETE = 10;

    /** */
    private Integer safeDays;

    /** */
    private Integer numOfItemsToDel;

    public static CleanerConfig getDefaultCleanerConfig() {
        CleanerConfig cfg = new CleanerConfig();
        cfg.safeDays = DEFAULT_SAVE_DAYS;
        cfg.numOfItemsToDel = DEFAULT_NUMBER_OF_ITEMS_TO_DELETE;
        return cfg;
    }

    /** */
    public Integer getSafeDays() {
        return safeDays == null || safeDays < 0 ? DEFAULT_SAVE_DAYS : safeDays;
    }

    /** */
    public Integer getNumOfItemsToDel() {
        return numOfItemsToDel == null || numOfItemsToDel < 0 ? DEFAULT_NUMBER_OF_ITEMS_TO_DELETE : numOfItemsToDel;
    }
}
