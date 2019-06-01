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

package org.apache.ignite.ci.analysis;

import java.io.File;
import org.apache.ignite.ci.logs.BuildLogStreamChecker;
import org.apache.ignite.ci.logs.handlers.TestLogHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpInMemoryHandler;

import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.JAVA_LEVEL_DEADLOCK;

/**
 *
 */
public class LogCheckTask {
    LogCheckResult result;
    private File zipFile;
    final ThreadDumpInMemoryHandler threadDumpCp = new ThreadDumpInMemoryHandler();

    /** Test logger handler. */
    final TestLogHandler testLogHandler = new TestLogHandler();

    public LogCheckTask(File zipFile) {
        this.zipFile = zipFile;
    }

    public LogCheckResult getResult() {
        return result;
    }

    public BuildLogStreamChecker createChecker() {
        return new BuildLogStreamChecker(threadDumpCp, testLogHandler);
    }

    public void finalize(boolean isIncompleteSuite) {
        LogCheckResult logCheckRes = testLogHandler.getResult(isIncompleteSuite);

        if (isIncompleteSuite)
            logCheckRes.setLastThreadDump(threadDumpCp.getLastThreadDump());
        else if (logCheckRes.hasProblem(JAVA_LEVEL_DEADLOCK))
            logCheckRes.setLastThreadDump(threadDumpCp.getLastThreadDump());

        this.result = logCheckRes;
    }
}
