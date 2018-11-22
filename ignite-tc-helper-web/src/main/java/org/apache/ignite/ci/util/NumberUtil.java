package org.apache.ignite.ci.util;

import javax.annotation.Nullable;

public class NumberUtil {
    public static int parseInt(@Nullable String val, int defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }
}
