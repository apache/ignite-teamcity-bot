package org.apache.ignite.ci.analysis.mode;

public enum LatestRebuildMode {
    /** None rebuilds are applied. */
    NONE,
    /** replace builds with Latest rebuild. */
    LATEST,
    /** Collect history of builds. */
    ALL
}
