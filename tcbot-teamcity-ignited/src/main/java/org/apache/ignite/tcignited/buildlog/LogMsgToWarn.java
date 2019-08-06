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

import javax.annotation.Nullable;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;

/**
 *
 */
//todo make non static
//todo include test name
//todo addBuild NPE
public class LogMsgToWarn {

    private static final String JAVA_LEVEL_DEADLOCK_TXT = " Java-level deadlock:";

    @Deprecated
    public static boolean needWarn(String line) {
        return line.contains("java.lang.AssertionError:")
            || line.contains(JAVA_LEVEL_DEADLOCK_TXT)
            || line.contains("Critical failure. Will be handled accordingly to configured handler");
    }

    @Nullable
    public static String getProblemCode(String line) {
        if (line.contains(JAVA_LEVEL_DEADLOCK_TXT))
            return ProblemOccurrence.JAVA_LEVEL_DEADLOCK;

        return null;
    }
}
