package org.apache.ignite.ci.analysis;

import java.io.File;
import org.apache.ignite.ci.logs.BuildLogStreamChecker;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpInMemoryHandler;

/**
 * Created by Дмитрий on 24.03.2018
 */
public class LogCheckTask {
    LogCheckResult result;
    private File zipFile;
    final ThreadDumpInMemoryHandler threadDumpCp = new ThreadDumpInMemoryHandler();
    final LastTestLogCopyHandler lastTestCp = new LastTestLogCopyHandler();

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
        return new BuildLogStreamChecker(threadDumpCp, lastTestCp);
    }

    public void finalize(boolean dumpLastTest) {
        LogCheckResult logCheckResult = new LogCheckResult();
        if (dumpLastTest) {
            logCheckResult.setLastStartedTest(lastTestCp.getLastTestName());
            logCheckResult.setLastThreadDump(threadDumpCp.getLastThreadDump());
        }
        setResult(logCheckResult);
    }
}
