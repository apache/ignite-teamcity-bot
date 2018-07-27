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

package org.apache.ignite.ci.util;

import java.time.Duration;
import org.jetbrains.annotations.Nullable;

/**
 * Created by dpavlov on 02.11.2017.
 */
public class TimeUtil {
    /**
     * @param ms
     * @return string representation of time duration
     */
    public static String getDurationPrintable(@Nullable Long ms) {
        if (ms == null)
            return "";

        String durationStr = Duration.ofMillis(ms).toString();
        if (durationStr.length() > 2)
            return humanReadableFormat(durationStr);

        return durationStr;
    }

    private static String humanReadableFormat(String durationStr) {
        return durationStr
            .substring(2)
            .replaceAll("(\\d[HMS])(?!$)", "$1 ")
            .toLowerCase();
    }
}
