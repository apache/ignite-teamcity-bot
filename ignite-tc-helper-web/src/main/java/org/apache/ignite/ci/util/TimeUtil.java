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
