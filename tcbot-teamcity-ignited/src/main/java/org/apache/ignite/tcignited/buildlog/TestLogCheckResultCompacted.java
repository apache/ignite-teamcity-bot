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

import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.tcbot.persistence.Persisted;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Persisted
class TestLogCheckResultCompacted implements ITestLogCheckResult {
    /** Warnings found. */
    @Nullable
    private List<StringFieldCompacted> warns;

    /** Count of lines. */
    private int cntLines = 0;

    /** Count of bytes. */

    private int cntBytes = 0;

    void addWarning(String line) {
        if (warns == null)
            warns = new ArrayList<>();

        warns.add(new StringFieldCompacted(line));
    }

    @Nonnull
    public List<String> getWarns() {
        return warns == null
                ? Collections.emptyList()
                : warns.stream().map(StringFieldCompacted::getValue).collect(Collectors.toList());
    }


    public void addLineStat(String line) {
        int i = line.length() + 1; //here suppose UTF-8, 1 byte per char; 1 newline char
        cntLines++;
        cntBytes += i;
    }

    public int getLogSizeBytes() {
        return cntBytes;
    }
}
