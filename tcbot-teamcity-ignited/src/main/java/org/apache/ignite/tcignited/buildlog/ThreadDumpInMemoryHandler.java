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
import javax.annotation.Nullable;

/**
 * Saves last observed thread dump. Use one instance per one file, class is stateful and not thread safe
 */
public class ThreadDumpInMemoryHandler implements ILineHandler {
    public static final String FULL_THREAD_DUMP = "Full thread dump ";

    @Nullable private StringBuilder currentThDump = null;

    private String lastThreadDump = null;

    @Override public void accept(String line, File fromLogFile) {
        if (currentThDump == null && line.contains(FULL_THREAD_DUMP))
            currentThDump = new StringBuilder();

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

    /** {@inheritDoc} */
    @Override public void close() {
        closeCurrentIfNeed();
    }

    @Nullable public String getLastThreadDump() {
        return lastThreadDump;
    }

}
