package org.apache.ignite.ci.tcmodel.result.problems;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by dpavlov on 03.08.2017
 */
public class ProblemOccurrence {
    public static final String BUILD_FAILURE_ON_MESSAGE = "BuildFailureOnMessage";
    private static final String TC_EXIT_CODE = "TC_EXIT_CODE";
    private static final String TC_OOME = "TC_OOME";

    @XmlAttribute public String id;
    @XmlAttribute public String identity;
    @XmlAttribute public String type;
    @XmlAttribute public String href;

    public boolean isExecutionTimeout() {
        return "TC_EXECUTION_TIMEOUT".equals(type);
    }

    public boolean isFailedTests() {
        return "TC_FAILED_TESTS".equals(type);
    }

    public boolean isShaphotDepProblem() {
        return "SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE".equals(type);
    }

    public boolean isJvmCrash() {
        return "TC_JVM_CRASH".equals(type);
    }

    public boolean isOome() {
        return TC_OOME.equals(type);
    }

    public boolean isExitCode() {
        return TC_EXIT_CODE.equals(type);
    }
}
