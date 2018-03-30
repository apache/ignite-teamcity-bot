package org.apache.ignite.ci.logs;

public interface ILogProductSpecific {
    boolean isTestStarting(String line);

    boolean isTestStopping(String line);
}
