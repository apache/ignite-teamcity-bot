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
package org.apache.ignite.ci.teamcity.pure;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.jetbrains.annotations.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BuildHistoryEmulator {
    private ArrayList<BuildRef> sharedState;

    public BuildHistoryEmulator(ArrayList<BuildRef> sharedState) {
        this.sharedState = sharedState;
    }

    @Nullable public InputStream handleUrl(String url) {
        InputStream stream = null;
        if (!url.contains("/app/rest/latest/builds?locator=defaultFilter:false")) {
            return stream;
        }

        int cnt = getIntFromLocator(url, "count:", 100);
        int start = getIntFromLocator(url, "start:", 100);

        int totalRemained = sharedState.size() - start;

        StringBuffer buf = new StringBuffer();

        buf.append("<builds count=\"1000\" href=\"/app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:0\" nextHref=\"/app/rest/latest/builds?locator=defaultFilter:false,count:1000,start:1000\">\n" +
            " ");

        buf.append("</builds>");

        byte[] bytes = buf.toString().getBytes(UTF_8);
        return new ByteInputStream(bytes, bytes.length);
    }

    public int getIntFromLocator(String url, String prefix, int def) {
        Pattern compile = Pattern.compile(prefix + "[0-9]*");
        Matcher m = compile.matcher(url);
        if (!m.find())
            return def;

        String cntStr = m.group(0);

        System.err.println(cntStr == null);

        return Integer.parseInt(cntStr.substring(prefix.length()));

    }
}
