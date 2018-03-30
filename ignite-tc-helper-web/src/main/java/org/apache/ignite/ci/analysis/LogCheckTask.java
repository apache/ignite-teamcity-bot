package org.apache.ignite.ci.analysis;

import java.io.File;
import org.apache.ignite.ci.logs.BuildLogStreamChecker;
import org.apache.ignite.ci.logs.handlers.TestLogHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpInMemoryHandler;

/**
 * Created by Дмитрий on 24.03.2018
 */
public class LogCheckTask {
    LogCheckResult result;
    private File zipFile;
    final ThreadDumpInMemoryHandler threadDumpCp = new ThreadDumpInMemoryHandler();

    final TestLogHandler testLogHandler = new TestLogHandler();

    public LogCheckTask(File zipFile) {
        this.zipFile = zipFile;
    }

    public void setResult(LogCheckResult result) {
        this.result = result;
    }

    public LogCheckResult getResult() {
        return result;
    }

    public BuildLogStreamChecker createChecker() {
        return new BuildLogStreamChecker(threadDumpCp, testLogHandler);
    }

    public void finalize(boolean isIncompleteSuite) {
        LogCheckResult logCheckResult = new LogCheckResult();
        if (isIncompleteSuite) {
            logCheckResult.setLastStartedTest(testLogHandler.getLastTestName());
            logCheckResult.setLastThreadDump(threadDumpCp.getLastThreadDump());
        }

        logCheckResult.setTests(testLogHandler.getTests());

        setResult(logCheckResult);
    }
}
