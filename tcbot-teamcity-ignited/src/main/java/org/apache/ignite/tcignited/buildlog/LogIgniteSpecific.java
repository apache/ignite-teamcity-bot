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

/**
 * Ignite specific logic related to build logs.
 */
public class LogIgniteSpecific implements ILogProductSpecific {
    private static final String JAVA_LEVEL_DEADLOCK_TXT = " Java-level deadlock:";

    /** Starting test. */
    private static final String STARTING_TEST = ">>> Starting test: ";
    /** Test name end. */
    private static final String TEST_NAME_END = " <<<";

    /** {@inheritDoc} */
    @Override public boolean isTestStarting(String line) {
        return line.contains(STARTING_TEST) && line.contains(TEST_NAME_END);
    }

    /** {@inheritDoc} */
    @Override public boolean needWarn(String line) {
        return line.contains("java.lang.AssertionError:")
            || line.contains(JAVA_LEVEL_DEADLOCK_TXT)
            || line.contains("Critical failure. Will be handled accordingly to configured handler");
    }
}
