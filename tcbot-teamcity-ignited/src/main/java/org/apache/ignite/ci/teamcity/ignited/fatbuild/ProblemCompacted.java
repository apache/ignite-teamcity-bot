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

package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ProblemCompacted {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ProblemCompacted.class);

    /** Id  */
    private int id = -1;
    private int type = -1;
    private int identity = -1;

    /** Actual build id. */
    private int actualBuildId = -1;

    // later details may be needed @Nullable private byte[] details;

    /**
     * Default constructor.
     */
    public ProblemCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param problemOccurrence TestOccurrence.
     */
    public ProblemCompacted(IStringCompactor compactor, ProblemOccurrence problemOccurrence) {
        String problemIdStr = problemOccurrence.id();
        if (!Strings.isNullOrEmpty(problemIdStr)) {
            try {
                final Integer problemId = TestCompactedV2.extractIdPrefixed(problemIdStr, "problem:(id:", ")");
                if (problemId != null)
                    id = problemId;
            } catch (Exception e) {
                logger.error("Failed to handle TC response: " + problemIdStr, e);
            }
        }

        type = compactor.getStringId(problemOccurrence.type);
        identity = compactor.getStringId(problemOccurrence.identity);


        if (problemOccurrence.buildRef != null && problemOccurrence.buildRef.getId() != null)
            actualBuildId = problemOccurrence.buildRef.getId();

    }


    public ProblemOccurrence toProblemOccurrence(IStringCompactor compactor, int buildId) {
        ProblemOccurrence occurrence = new ProblemOccurrence();

        String fullStrId =
                "problem:(id:" + id + ")," +
                        "build:(id:" + buildId + ")";
        occurrence.id(fullStrId);
        occurrence.type = compactor.getStringFromId(type);
        occurrence.identity = compactor.getStringFromId(identity);
        occurrence.href = "problemOccurrences?locator=" + fullStrId;

        if (actualBuildId > 0) {
            BuildRef buildRef = new BuildRef();

            buildRef.setId(actualBuildId);

            occurrence.buildRef = buildRef;
        }

        return occurrence;
    }

    private int id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProblemCompacted that = (ProblemCompacted) o;
        return id == that.id &&
                type == that.type &&
                identity == that.identity &&
                actualBuildId == that.actualBuildId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, type, identity, actualBuildId);
    }

    /** */
    public boolean isCompilationError(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_COMPILATION_ERROR) == type;
    }


    /** */
    public boolean isBuildFailureOnMetric(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.BUILD_FAILURE_ON_METRIC) == type;
    }

    /**
     * @param compactor Compactor.
     */
    public boolean isExecutionTimeout(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_EXECUTION_TIMEOUT) == type;
    }

    public String type(IStringCompactor compactor) {
        return compactor.getStringFromId(type);
    }

    public boolean isJvmCrash(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_JVM_CRASH) == type;
    }

    public boolean isOome(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_OOME) == type;
    }

    public boolean isExitCode(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_EXIT_CODE) == type;
    }

    public boolean isFailedTests(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.TC_FAILED_TESTS) == type;
    }

    public boolean isSnapshotDepProblem(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.SNAPSHOT_DEPENDENCY_ERROR) == type
                || compactor.getStringId(ProblemOccurrence.SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE) == type;
    }

    public int type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("type", type)
            .add("identity", identity)
            .add("actualBuildId", actualBuildId)
            .toString() + "\n";
    }

    /**
     * @param compactor Compactor.
     * @return if this problem is critical.
     */
    public boolean isCriticalProblem(IStringCompactor compactor) {
        return isExecutionTimeout(compactor)
            || isJvmCrash(compactor)
            || isBuildFailureOnMetric(compactor)
            || isCompilationError(compactor)
            || isExitCode(compactor)
            || isOome(compactor);
    }

    public boolean isBuildFailureOnMessage(IStringCompactor compactor) {
        return compactor.getStringId(ProblemOccurrence.BUILD_FAILURE_ON_MESSAGE) == type;
    }
}
