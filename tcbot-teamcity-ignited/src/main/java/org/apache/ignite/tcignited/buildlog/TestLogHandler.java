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

import java.io.File;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import javax.inject.Inject;

/**
 * Use one instance per one file, class is statefull and not thread safe
 */
public class TestLogHandler implements ILineHandler {
    private static final String STARTING_TEST = ">>> Starting test: ";
    private static final String TEST_NAME_END = " <<<";

    public static final TestLogCheckResultCompacted FAKE_RESULT = new TestLogCheckResultCompacted();

    private String currentTestName = null;
    private File workFolder;

    /** Result. */
    private LogCheckResultCompacted res = new LogCheckResultCompacted();

    @Inject
    private IStringCompactor compactor;

    @Inject
    private ILogProductSpecific logSpecific;

    @Override public void accept(String line, File fromLogFile) {
        if (workFolder == null)
            workFolder = fromLogFile.getParentFile();

        if (logSpecific.isTestStarting(line)) {
            if (currentTestName != null)
                currentTestName = null;

            this.currentTestName = line.substring(line.indexOf(STARTING_TEST) + STARTING_TEST.length(), line.indexOf(TEST_NAME_END));
        }

        if (currentTestName == null)
            return;

        curTest().addLineStat(line);

        if (logSpecific.needWarn(line))
            curTest().addWarning(line);

        String problemCode = LogMsgToWarn.getProblemCode(line);

        if (problemCode != null)
            res.addProblem(problemCode, compactor);
    }

    private TestLogCheckResultCompacted curTest() {
        String curName = getLastTestName();

        return curName == null ? FAKE_RESULT : res.getOrCreateTestResult(curName);
    }

    /** {@inheritDoc} */
    @Override public void close() {

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

    public LogCheckResultCompacted getResult(boolean isIncompleteSuite) {
        if (isIncompleteSuite)
            res.setLastStartedTest(getLastTestName(), compactor);

        return res;
    }
}
