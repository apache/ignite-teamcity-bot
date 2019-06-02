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

package org.apache.ignite.tcignited.buildlog;


import org.apache.ignite.tcbot.persistence.IStringCompactor;

import javax.inject.Inject;

import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.JAVA_LEVEL_DEADLOCK;

/**
 *
 */
class LogCheckTask {
    private LogCheckResultCompacted result;
    final ThreadDumpInMemoryHandler threadDumpCp = new ThreadDumpInMemoryHandler();

    /** Test logger handler. */
    @Inject
    private TestLogHandler testLogHandler;

    @Inject
    private IStringCompactor compactor;

    public LogCheckTask() {
    }

    public LogCheckResultCompacted getResult() {
        return result;
    }

    public BuildLogStreamChecker createChecker() {
        return new BuildLogStreamChecker(threadDumpCp, testLogHandler);
    }

    public LogCheckResultCompacted finalize(boolean isIncompleteSuite) {
        LogCheckResultCompacted logCheckRes = testLogHandler.getResult(isIncompleteSuite);

        if (isIncompleteSuite)
            logCheckRes.setLastThreadDump(threadDumpCp.getLastThreadDump());
        else if (logCheckRes.hasProblem(JAVA_LEVEL_DEADLOCK, compactor))
            logCheckRes.setLastThreadDump(threadDumpCp.getLastThreadDump());

        this.result = logCheckRes;

        return result;
    }
}
