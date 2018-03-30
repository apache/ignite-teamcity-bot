package org.apache.ignite.ci.logs;

public class LogIgniteSpecific implements ILogProductSpecific {

    private static final String STARTING_TEST = ">>> Starting test: ";
    private static final String TEST_NAME_END = " <<<";

    public static final String STOPPING_TEST = ">>> Stopping test: ";

    @Override public boolean isTestStarting(String line) {
       return line.contains(STARTING_TEST) && line.contains(TEST_NAME_END);
    }

    @Override public boolean isTestStopping(String line) {
        return  line.contains(STOPPING_TEST);
    }
}
