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

        String s = Duration.ofMillis(ms).toString();
        if (s.length() > 2)
            s = s.substring(2);
        return s;
    }
}
