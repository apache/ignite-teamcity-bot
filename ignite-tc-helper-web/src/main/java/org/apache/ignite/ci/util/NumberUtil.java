package org.apache.ignite.ci.util;

import javax.annotation.Nullable;

public class NumberUtil {
    /**
     * @param val Value.
     * @param defaultValue Default value.
     * @return Return parsed integer from string value. If value is null or is not integer, return default value.
     */
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
