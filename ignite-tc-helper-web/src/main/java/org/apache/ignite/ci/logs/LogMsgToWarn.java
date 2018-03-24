package org.apache.ignite.ci.logs;

/**
 * Created by Дмитрий on 24.03.2018
 */
public class LogMsgToWarn {
    public static boolean needWarn(String line) {
        return line.contains("java.lang.AssertionError:");
    }
}
