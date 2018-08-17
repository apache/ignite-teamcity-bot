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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.ignite.ci.logs.ILineHandler;

/**
 * Use one instance per one file, class is statefull and not thread safe
 */
@NotThreadSafe
public class ThreadDumpCopyHandler implements ILineHandler {
    private static final String ENDL = String.format("%n");
    private FileWriter currentThDump;
    private int fileIdx = 0;
    private Integer lastFileIdx = null;

    @Override public void accept(String line, File fromLogFile) {
        try {
            acceptX(line, fromLogFile);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void acceptX(String line, File fromLogFile) throws IOException {
        if (currentThDump == null && line.contains(ThreadDumpInMemoryHandler.FULL_THREAD_DUMP)) {
            lastFileIdx = fileIdx;
            String curFileName = fileName(fileIdx);
            currentThDump = new FileWriter(new File(fromLogFile.getParentFile(), curFileName));
            fileIdx++;
        }

        if (line.startsWith("["))
            closeCurrentIfNeed();

        if (currentThDump != null) {
            currentThDump.write(line);
            currentThDump.write(ENDL);
        }

    }

    @Nonnull public static String fileName(int idx) {
        return "ThreadDump" + idx + ".log";
    }

    private void closeCurrentIfNeed() throws IOException {
        if (currentThDump != null) {
            currentThDump.close();
            currentThDump = null;
        }
    }

    @Override public void close() throws Exception {
        closeCurrentIfNeed();
    }

    @Nullable public Integer getLastFileIdx() {
        return lastFileIdx;
    }

}
