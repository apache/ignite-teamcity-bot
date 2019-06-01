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

package org.apache.ignite.tcservice.model.result.problems;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.tcservice.model.hist.BuildRef;

/**
 * One build problem. Contains its type.
 *
 * http://javadoc.jetbrains.net/teamcity/openapi/8.0/constant-values.html
 */
public class ProblemOccurrence {
    public static final String BUILD_FAILURE_ON_MESSAGE = "BuildFailureOnMessage";
    public static final String BUILD_FAILURE_ON_METRIC = "BuildFailureOnMetric";
    public static final String TC_EXIT_CODE = "TC_EXIT_CODE";
    public static final String TC_OOME = "TC_OOME";
    public static final String TC_EXECUTION_TIMEOUT = "TC_EXECUTION_TIMEOUT";
    public static final String TC_FAILED_TESTS = "TC_FAILED_TESTS";
    public static final String TC_JVM_CRASH = "TC_JVM_CRASH";
    public static final String OTHER = "OTHER";
    public static final String SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE = "SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE";
    public static final String SNAPSHOT_DEPENDENCY_ERROR = "SNAPSHOT_DEPENDENCY_ERROR";

    /** */
    public static final String TC_COMPILATION_ERROR = "TC_COMPILATION_ERROR";

    /** Java level deadlock: Detected by log processing, not by teamcity */
    public static final String JAVA_LEVEL_DEADLOCK = "JAVA_LEVEL_DEADLOCK";

    @XmlAttribute public String id;
    @XmlAttribute public String identity;
    @XmlAttribute public String type;
    @XmlAttribute public String href;
    /**
     * Build reference, may be filled by TC and by
     * May be null for old persisted entries.
     */
    @Nullable public BuildRef buildRef;

    /** */
    public boolean isCompilationError() {
        return TC_COMPILATION_ERROR.equals(type);
    }

    /** */
    public boolean isFailureOnMetric() {
        return BUILD_FAILURE_ON_METRIC.equals(type);
    }

    public boolean isExecutionTimeout() {
        return TC_EXECUTION_TIMEOUT.equals(type);
    }

    public boolean isFailedTests() {
        return TC_FAILED_TESTS.equals(type);
    }

    public boolean isSnapshotDepProblem() {
        return SNAPSHOT_DEPENDENCY_ERROR.equals(type) || SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE.equals(type);
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

    public boolean isJavaLevelDeadlock() {
        return JAVA_LEVEL_DEADLOCK.equals(type);
    }

    public String id() {
        return id;
    }

    public void id(String fullStrId) {
        this.id = fullStrId;
    }

    public void setType(String type) {
        this.type = type;
    }
}
