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

package org.apache.ignite.tcbot.engine.build;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFailuresAiPromptBuilderTest {
    @Test
    public void restMaxDetailsCharsUsesHardCap() {
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(null));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(0));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(-1));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS + 1));
        assertEquals(1024, TestFailuresAiPromptBuilder.restMaxDetailsChars(1024));
    }
}
