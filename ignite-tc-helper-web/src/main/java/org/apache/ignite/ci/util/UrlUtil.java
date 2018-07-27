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
