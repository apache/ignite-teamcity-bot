/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.tcmodel.result.problems;

import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;

/**
 * One build problem. Contains its type.
 */
public class ProblemOccurrence {
    public static final String BUILD_FAILURE_ON_MESSAGE = "BuildFailureOnMessage";
    public static final String TC_EXIT_CODE = "TC_EXIT_CODE";
    public static final String TC_OOME = "TC_OOME";
    public static final String TC_EXECUTION_TIMEOUT = "TC_EXECUTION_TIMEOUT";
    public static final String TC_FAILED_TESTS = "TC_FAILED_TESTS";
    public static final String TC_JVM_CRASH = "TC_JVM_CRASH";
    public static final String OTHER = "OTHER";
    public static final String SNAPSHOT_DEPENDENCY_ERROR = "SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE";

    @XmlAttribute public String id;
    @XmlAttribute public String identity;
    @XmlAttribute public String type;
    @XmlAttribute public String href;
    public BuildRef buildRef;

    public boolean isExecutionTimeout() {
        return TC_EXECUTION_TIMEOUT.equals(type);
    }

    public boolean isFailedTests() {
        return TC_FAILED_TESTS.equals(type);
    }

    public boolean isSnapshotDepProblem() {
        return SNAPSHOT_DEPENDENCY_ERROR.equals(type);
    }

    public boolean isJvmCrash() {
        return TC_JVM_CRASH.equals(type);
    }

    public boolean isOome() {
        return TC_OOME.equals(type);
    }

    public boolean isExitCode() {
        return TC_EXIT_CODE.equals(type);
    }

    public boolean isOther(){
        return !isFailedTests()
            && !isSnapshotDepProblem()
            && !isExecutionTimeout()
            && !isJvmCrash()
            && !isExitCode()
            && !isOome();
    }
}
