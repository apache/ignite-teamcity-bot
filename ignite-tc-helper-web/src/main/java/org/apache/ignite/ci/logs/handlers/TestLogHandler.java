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

package org.apache.ignite.ci.logs.handlers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.TestLogCheckResult;
import org.apache.ignite.ci.logs.ILineHandler;
import org.apache.ignite.ci.logs.ILogProductSpecific;
import org.apache.ignite.ci.logs.LogIgniteSpecific;
import org.apache.ignite.ci.logs.LogMsgToWarn;

/**
 * Use one instance per one file, class is statefull and not thread safe
 */
public class TestLogHandler implements ILineHandler {
    private static final String STARTING_TEST = ">>> Starting test: ";
    private static final String TEST_NAME_END = " <<<";
    private static final String ENDL = String.format("%n");

    private static final ILogProductSpecific logSpecific =new LogIgniteSpecific();
    public static final TestLogCheckResult FAKE_RESULT = new TestLogCheckResult();
    private final List<String> curTestLog = new ArrayList<>();

    /**
     * Test name -> its log check results
     */
    private final Map<String, TestLogCheckResult> tests = new TreeMap<>();

    private String currentTestName = null;
    private File workFolder;

    /** Dump last test. This mode is enabled if suite was timed out */
    private boolean saveLastTestToFile;

    private static boolean SAVE_LOG_STAT = true;

    @Override public void accept(String line, File fromLogFile) {
        if (workFolder == null)
            workFolder = fromLogFile.getParentFile();

        if (logSpecific.isTestStarting(line)) {
            if (currentTestName != null) {
                currentTestName = null;
                curTestLog.clear();
            }
            String startTest = line.substring(line.indexOf(STARTING_TEST) + STARTING_TEST.length(), line.indexOf(TEST_NAME_END));


            this.currentTestName = startTest;
        }
        else if (currentTestName != null && logSpecific.isTestStopping(line)) {
            //currentTestName = null;
            //curTestLog.clear();
        }

        if (currentTestName == null)
            return;

        if(SAVE_LOG_STAT)
            curTest().addLineStat(line);

        if (LogMsgToWarn.needWarn(line))
            curTest().addWarning(line);

        if (!saveLastTestToFile)
            return;

        curTestLog.add(line);

        if (currentTestName != null) {
            if (line.contains("Test has been timed out [")) {
                dumpCurrentToFile("timedOut_");
                currentTestName = null;
                curTestLog.clear();
            }
        }
    }

    public TestLogCheckResult curTest() {
        String curName = getLastTestName();

        if (curName == null)
            return FAKE_RESULT;

        return tests.computeIfAbsent(curName, k -> new TestLogCheckResult());
    }

    @Override public void close() throws Exception {
        if (saveLastTestToFile && currentTestName != null && !curTestLog.isEmpty())
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
            curTestLog.forEach(line -> {
                try {
                    writer.write(line);
                    writer.write(ENDL);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        curTestLog.clear();
    }

    /**
     * @return returns last observed test name
     */
    public String getLastTestName() {
        if (currentTestName == null)
            return null;

        String str = currentTestName.replaceAll("#", ".");

        int cfgVariationMarker = str.indexOf("-[");
        if (cfgVariationMarker > 0)
            return str.substring(0, cfgVariationMarker);

        return str;
    }

    public void setSaveLastTestToFile(boolean saveLastTestToFile) {
        this.saveLastTestToFile = saveLastTestToFile;
    }


    public Map<String, TestLogCheckResult> getTests() {
        return tests;
    }
}
