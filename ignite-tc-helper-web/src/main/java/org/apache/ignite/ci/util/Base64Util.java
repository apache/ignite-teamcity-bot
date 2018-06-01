package org.apache.ignite.ci.util;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Created by dpavlov on 02.08.2017
 */
public class Base64Util {
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public static String encodeUtf8String(String data) {
        return encodeBytesToString(data.getBytes(CHARSET));
    }

    @NotNull
    public static String encodeBytesToString(byte[] bytes) {
        final byte[] encode = Base64.getEncoder().encode(bytes);
        return new String(encode, CHARSET);
    }

    @NotNull
    public static byte[] decodeString(String string) {
        return Base64.getDecoder().decode(string.getBytes(CHARSET));
    }

}
