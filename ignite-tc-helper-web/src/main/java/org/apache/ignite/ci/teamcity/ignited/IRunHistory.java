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
package org.apache.ignite.ci.teamcity.ignited;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Test or Build run statistics.
 */
public interface IRunHistory {
    public int getRunsCount();
    public int getFailuresCount();

    public static String getPercentPrintable(float percent) {
        return String.format("%.1f", percent).replace(".", ",");
    }

    public default String getFailPercentPrintable() {
        return getPercentPrintable(getFailRate() * 100.0f);
    }

    public int getFailuresAllHist();

    public int getRunsAllHist();

    public default float getFailRateAllHist() {
        if (getRunsAllHist() == 0)
            return 0.0f;

        return 1.0f * getFailuresAllHist() / getRunsAllHist();
    }

    public default String getFailPercentAllHistPrintable() {
        return getPercentPrintable(getFailRateAllHist() * 100.0f);
    }

    /**
     * @return fail rate as float.
     */
    public default float getFailRate() {
        int runs = getRunsCount();

        if (runs == 0)
            return 0.0f;

        return 1.0f * getFailuresCount() / runs;
    }

    @Nullable
    List<Integer> getLatestRunResults();

    String getFlakyComments();

    public default String getCriticalFailPercentPrintable() {
        return IRunHistory.getPercentPrintable(getCriticalFailRate() * 100.0f);
    }

    /**
     * @return float representing fail rate
     */
    public default float getCriticalFailRate() {
        int runs = getRunsCount();

        if (runs == 0)
            return 1.0f;

        return 1.0f * getCriticalFailuresCount() / runs;
    }


    public int getCriticalFailuresCount();
}
