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

package org.apache.ignite.tcbot.common.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

import javax.annotation.Nullable;

/**
 * Time and duration util.
 */
public class TimeUtil {
    /**
     * @param ms millis passed.
     * @return string representation of time duration
     */
    public static String millisToDurationPrintable(@Nullable Long ms) {
        if (ms == null)
            return "";

        String durationStr = Duration.ofMillis(ms).toString();
        if (durationStr.length() > 2)
            return humanReadableFormat(durationStr);

        return durationStr;
    }

    /**
     * @param durationStr Duration string.
     * @return human readable string of duration.
     */
    private static String humanReadableFormat(String durationStr) {
        return durationStr
            .substring(2)
            .replaceAll("(\\d[HMS])(?!$)", "$1 ")
            .toLowerCase();
    }

    public static String nanosToDurationPrintable(long ns) {
        String durationStr = Duration.ofNanos(ns).toString();

        if (durationStr.length() > 2)
            return humanReadableFormat(durationStr);

        return durationStr;
    }

    public static String timestampToDateTimePrintable(long l) {
        if (l == 0) return "-";

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy");

        return simpleDateFormat.format(new Date(l));
    }

    /**
     * @param date String with date format {@code yyyyMMdd'T'HHmmssZ}.
     * @return Timestamp for given date.
     */
    public static long tcSimpleDateToTimestamp(String date) {
        try {
            return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ").parse(date).getTime();
        }
        catch (ParseException e) {
            System.err.println("Exception happened when TimeUtil tried to convert date into timestamp [" +
                "date=" + date + ", err=" + e.getMessage() + ']');
        }

        return 0;
    }

    /**
     * @param ts Timestamp.
     * @return String with date format {@code yyyyMMdd'T'HHmmssZ} for given timestamp.
     */
    public static String timestampToTcSimpleDate(long ts) {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ").format(new Date(ts));
    }

    public static String timestampForLogsSimpleDate(long ts) {
        return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(ts));
    }
}
