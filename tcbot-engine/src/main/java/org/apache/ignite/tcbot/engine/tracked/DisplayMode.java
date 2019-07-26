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
package org.apache.ignite.tcbot.engine.tracked;

import com.google.common.base.Strings;

public enum DisplayMode {
    /** Technical mode for skipping UI conversions. */ None("None"),
    /** Show only  Failures. */ OnlyFailures("Failures"),
    /** Show all suites. */ ShowAllSuites("AllSuites");

    private String name;

    DisplayMode(String name) {
        this.name = name;
    }

    public static DisplayMode parseStringValue(String v) {
        if (Strings.isNullOrEmpty(v))
            return OnlyFailures;
        DisplayMode[] values = DisplayMode.values();

        for (int i = 0; i < values.length; i++) {
            DisplayMode val = values[i];
            if (val.name.equals(v))
                return val;
        }

        return OnlyFailures;
    }
}
