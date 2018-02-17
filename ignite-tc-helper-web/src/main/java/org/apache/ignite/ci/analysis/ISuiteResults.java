package org.apache.ignite.ci.analysis;

/**
 * Results from one or several builds
 */
public interface ISuiteResults {
      boolean hasTimeoutProblem();

    boolean hasJvmCrashProblem();

    boolean hasOomeProblem();

    String suiteId();
}
