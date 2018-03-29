package org.apache.ignite.ci.logs;

/**
 * Created by Дмитрий on 24.03.2018
 */
//todo make non static
    //todo include test name
    //todo add NPE
public class LogMsgToWarn {
    public static boolean needWarn(String line) {
        return line.contains("java.lang.AssertionError:")
            || line.contains("Critical failure. Will be handled accordingly to configured handler");
    }
}
