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

package org.apache.ignite.tcservice;

import com.google.common.base.Strings;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.util.UrlUtil;

/**
 * Helper for TeamCity REST locator values.
 */
public final class TeamcityLocator {
    /** Characters with structural meaning inside locator values. */
    private static final String LOCATOR_SPECIAL_CHARS = "\\,:()";

    /** */
    private TeamcityLocator() {
        // No-op.
    }

    /**
     * @param buildTypeId Build type id.
     * @param branchName Branch name.
     * @param count Result page size.
     * @return Relative REST URL for build lookup by suite and branch.
     */
    public static String buildsByTypeAndBranch(String buildTypeId, @Nullable String branchName, int count) {
        return "app/rest/latest/builds?locator=defaultFilter:false,buildType:(id:" + value(buildTypeId) +
            "),branch:" + value(branchName) + ",count:" + count;
    }

    /**
     * @param val Raw locator value.
     * @return Locator-escaped and URL-encoded value.
     */
    public static String value(@Nullable String val) {
        return UrlUtil.escape(escapeLocatorValue(Strings.nullToEmpty(val)));
    }

    /**
     * @param val Raw locator value.
     * @return Locator-escaped value.
     */
    private static String escapeLocatorValue(String val) {
        StringBuilder res = new StringBuilder(val.length());

        for (int i = 0; i < val.length(); i++) {
            char ch = val.charAt(i);

            if (LOCATOR_SPECIAL_CHARS.indexOf(ch) >= 0)
                res.append('\\');

            res.append(ch);
        }

        return res.toString();
    }
}
