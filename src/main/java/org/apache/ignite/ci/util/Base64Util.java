package org.apache.ignite.ci.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Created by dpavlov on 02.08.2017
 */
public class Base64Util {
    public static String encodeUtf8String(String data) {
        final Charset charset = StandardCharsets.UTF_8;
        final byte[] encode = Base64.getEncoder().encode(data.getBytes(charset));
        return new String(encode, charset);
    }
}
