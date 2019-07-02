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
package org.apache.ignite.tcbot.engine.digest;

import org.apache.ignite.tcbot.common.util.TimeUtil;

import javax.annotation.Nullable;

/**
 *
 */
public class WeeklyFailuresDigest {
    /** Timestamp. */
    public long ts = System.currentTimeMillis();
    /** Failed tests. */
    public Integer failedTests;
    /** Failed suites. */
    public Integer failedSuites;
    /** Total tests. */
    public int totalTests;
    /** Trusted tests. */
    public int trustedTests;

    public String toHtml(@Nullable WeeklyFailuresDigest lastDigest) {
        StringBuilder res = new StringBuilder();
        res.append("Digest");
        if (lastDigest != null) {
            res.append(" from ");
            res.append(TimeUtil.timestampToDateTimePrintable(lastDigest.ts));
        }

        res.append(" till ");
        res.append(TimeUtil.timestampToDateTimePrintable(System.currentTimeMillis()));
        res.append("<br>");

        res.append("Trusted tests ").append(trustedTests);
        if (lastDigest != null) {
            int add = trustedTests - lastDigest.trustedTests;

            res.append("(");
            if (add >= 0)
                res.append("+");
            res.append(add);
            res.append(")");
        }
        res.append("<br>");
        res.append("Total executed tests ").append(totalTests);
        res.append("<br>");
        res.append("Failed tests ").append(failedTests);
        res.append("<br>");
        res.append("Failed suites ").append(failedSuites);
        res.append("<br>");
        return res.toString();
    }
}
