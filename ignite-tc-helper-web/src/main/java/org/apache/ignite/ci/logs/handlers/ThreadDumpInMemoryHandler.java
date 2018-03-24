package org.apache.ignite.ci.logs.handlers;

import java.io.File;
import javax.annotation.Nullable;
import org.apache.ignite.ci.logs.ILineHandler;

/**
 * Saves last observed thread dump. Use one instance per one file, class is stateful and not thread safe
 */
public class ThreadDumpInMemoryHandler implements ILineHandler {
    public static final String FULL_THREAD_DUMP = "Full thread dump ";

    @Nullable private StringBuilder currentThDump = null;

    private String lastThreadDump = null;

    @Override public void accept(String line, File fromLogFile) {
        if (currentThDump == null && line.contains(FULL_THREAD_DUMP)) {
            currentThDump = new StringBuilder();
        }

        if (line.startsWith("["))
            closeCurrentIfNeed();

        if (currentThDump != null) {
            currentThDump.append(line);
            currentThDump.append("\n");
        }
    }

    private void closeCurrentIfNeed() {
        if (currentThDump != null) {
            lastThreadDump = currentThDump.toString();

            currentThDump = null;
        }
    }

    @Override public void close() throws Exception {
        closeCurrentIfNeed();
    }

    @Nullable public String getLastThreadDump() {
        return lastThreadDump;
    }

}
