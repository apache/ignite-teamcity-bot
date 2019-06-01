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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBException;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.hist.Builds;
import org.apache.ignite.tcservice.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BuildHistoryEmulator {
    private ArrayList<BuildRef> sharedState;

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public BuildHistoryEmulator(ArrayList<BuildRef> sharedState) {
        this.sharedState = sharedState;
    }

    /**
     * @param url Url.
     */
    @Nullable public InputStream handleUrl(String url) throws JAXBException {
        if (!url.contains("app/rest/latest/builds?locator=defaultFilter:false"))
            return null;

        int cnt = getIntFromLocator(url, "count:", 100);
        int start = getIntFromLocator(url, "start:", 100);

        int totalBuilds = sharedState.size();
        int totalRemained = totalBuilds - start;
        if (totalRemained < 0)
            totalRemained = 0;

        int returnNow = Math.min(totalRemained, cnt);

        int nextStart = 0;
        if (totalBuilds > start + returnNow)
            nextStart = start + returnNow;

        Builds builds = createBuilds(cnt, returnNow, nextStart);
        List<BuildRef> buildsList = new ArrayList<>();

        for (int i = start; i < start + returnNow; i++)
            buildsList.add(sharedState.get(i));

        builds.builds(buildsList);

        return new ByteArrayInputStream(XmlUtil.save(builds).getBytes(UTF_8));
    }

    public Builds createBuilds(int cnt, int returnNow, int nextStart) {
        Builds builds = new Builds();
        builds.count(returnNow);
        if (nextStart > 0) {
            String buf = "app/rest/latest/builds?locator=defaultFilter:false,count:" +
                cnt +
                ",start:" +
                nextStart;
            builds.nextHref(buf);
        }

        return builds;

    }

    /**
     * @param url Url.
     * @param prefix Prefix.
     * @param def Def.
     */
    public int getIntFromLocator(String url, String prefix, int def) {
        Pattern compile = Pattern.compile(prefix + "[0-9]*");
        Matcher m = compile.matcher(url);
        if (!m.find())
            return def;

        String cntStr = m.group(0);

        if (cntStr == null)
            return def;

        return Integer.parseInt(cntStr.substring(prefix.length()));
    }
}
