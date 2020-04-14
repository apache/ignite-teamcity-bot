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
package org.apache.ignite.tcignited.history;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import java.util.List;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;

/**
 * Test or Build run statistics.
 */
public interface IRunHistory {

    public boolean isFlaky();

    public Iterable<Invocation> invocations();

    public Stream<Invocation> getInvocations();

    @Nullable
    List<Integer> getLatestRunResults();

    @Nullable String getFlakyComments();

    @Nullable
    public Integer detectTemplate(IEventTemplate t);

    public default String getCriticalFailPercentPrintable() {
        return getPercentPrintable(getCriticalFailRate() * 100.0f);
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

    public int getRunsCount();
    public int getFailuresCount();

    /**
     * Recent runs fail rate.
     * @return fail rate as float: 0.0...1.0
     */
    public default float getFailRate() {
        int runs = getRunsCount();

        if (runs == 0)
            return 0.0f;

        return 1.0f * getFailuresCount() / runs;
    }

    public default String getFailPercentPrintable() {
        return getPercentPrintable(getFailRate() * 100.0f);
    }


    public static String getPercentPrintable(float percent) {
        return String.format("%.1f", percent).replace(".", ",");
    }

}
