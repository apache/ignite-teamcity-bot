package org.apache.ignite.ci.logs.handlers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.logs.ILineHandler;

/**
 * Use one instance per one file, class is statefull and not thread safe
 */
public class LastTestLogCopyHandler implements ILineHandler {
    private static final String TEST_NAME_END = " <<<";
    private static final String ENDL = String.format("%n");
    private static final String STARTING_TEST = ">>> Starting test: ";

    private final List<String> lastTestLog = new ArrayList<>();
    private String currentTestName = null;
    private File workFolder;

    /** Dump last test. This mode is enabled if suite was timed out */
    private boolean dumpLastTest;

    @Override public void accept(String line, File fromLogFile) {

        if (workFolder == null)
            workFolder = fromLogFile.getParentFile();

        if (line.contains(STARTING_TEST) && line.contains(TEST_NAME_END)) {
            if (currentTestName != null) {
                currentTestName = null;
                lastTestLog.clear();
            }
            String startTest = line.substring(line.indexOf(STARTING_TEST) + STARTING_TEST.length(), line.indexOf(TEST_NAME_END));
            this.currentTestName = startTest;
        }
        else if (currentTestName != null && line.contains(">>> Stopping test: ")) {
            //currentTestName = null;
            //lastTestLog.clear();
        }

        if (currentTestName != null)
            lastTestLog.add(line);

        if (currentTestName != null) {
            if (line.contains("Test has been timed out [")) {
                dumpCurrentToFile("timedOut_");
                currentTestName = null;
                lastTestLog.clear();
            }
        }

    }

    @Override public void close() throws Exception {
        if (dumpLastTest && currentTestName != null && !lastTestLog.isEmpty())
            dumpCurrentToFile("lastStartedTest_");
    }

    private void dumpCurrentToFile(String logPrefix) {
        try {
            dumpCurrentToFileX(logPrefix);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void dumpCurrentToFileX(String logPrefix) throws IOException {
        String name = getLastTestName();
        try (FileWriter writer = new FileWriter(new File(workFolder, logPrefix + name + ".log"))) {
            lastTestLog.forEach(line -> {
                try {
                    writer.write(line);
                    writer.write(ENDL);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        lastTestLog.clear();
    }

    public String getLastTestName() {
        if (currentTestName == null)
            return null;
        return currentTestName.replaceAll("#", ".");
    }

    public void setDumpLastTest(boolean dumpLastTest) {
        this.dumpLastTest = dumpLastTest;
    }

}
