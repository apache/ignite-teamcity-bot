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

package org.apache.ignite.ci.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CommonScriptTest {
    @Test
    public void aiPromptSkipButtonRequestsCurrentContext() throws IOException {
        String js = readFile(commonJs());

        assertTrue(js.contains("buttonText: \"Use current context now\""));
        assertTrue(js.contains("nextMode: false"));
        assertTrue(js.contains("requestTextCommand(options, state, nextMode, skip.stepText)"));
        assertTrue(js.contains("return aiPromptUrlWithWaitForTc(url, waitForTc, processId)"));
        assertTrue(js.contains("\"waitForTc=\" + waitForTc"));
    }

    private static Path commonJs() {
        Path projectPath = Paths.get("src/main/webapp/js/common-1.7.js");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/js/common-1.7.js");
    }

    private static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
    }
}
