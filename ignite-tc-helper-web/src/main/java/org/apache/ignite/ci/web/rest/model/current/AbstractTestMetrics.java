package org.apache.ignite.ci.web.rest.model.current;

/**
 * UI model for test run statistics for suite, chain or server
 */
@SuppressWarnings("WeakerAccess") public abstract class AbstractTestMetrics {
    public Integer failedTests;

    /** Count of suites with critical build problems found */
    public Integer failedToFinish;

    public String durationPrintable;
}
