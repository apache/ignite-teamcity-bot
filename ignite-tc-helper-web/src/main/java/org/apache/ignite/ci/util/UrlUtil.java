package org.apache.ignite.ci.util;

import com.google.common.base.Strings;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

/**
 * Created by dpavlov on 31.10.2017.
 */
public class UrlUtil {
    private static final String ENC = "UTF-8";

    public static String escape(@Nullable final String value) {
        try {
            return URLEncoder.encode(Strings.nullToEmpty(value), ENC);
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return value;
        }
    }

    public static String escapeOrB64(String value) {
        if (Strings.nullToEmpty(value).contains("/")) {
            String idForRestEncoded = Base64Util.encodeUtf8String(value);
            return "($base64:" + idForRestEncoded + ")";
        }
        else
            return escape(value);
    }
}
