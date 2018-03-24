package org.apache.ignite.ci.analysis;

/**
 * Results from one or several builds
 */
public interface ISuiteResults {
    boolean hasTimeoutProblem();

    boolean hasJvmCrashProblem();

    boolean hasOomeProblem();

    boolean hasExitCodeProblem();


    default boolean hasSuiteIncompleteFailure() {
        return hasJvmCrashProblem()
            || hasTimeoutProblem()
            || hasOomeProblem()
            || hasExitCodeProblem();
    }

    String suiteId();
}
