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
package org.apache.ignite.tcignited.build;

import javax.annotation.Nullable;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public interface ITest {
    public int testName();
    public String testName(IStringCompactor compactor);

    public int status();

    public default boolean isIgnoredTest() {
        Boolean flag = getIgnoredFlag();

        return flag != null && flag;
    }

    public default boolean isMutedTest() {
        Boolean flag = getMutedFlag();

        return flag != null && flag;
    }

    public boolean isInvestigated();

    public Boolean getCurrentlyMuted();
    public Boolean getCurrInvestigatedFlag();

    @Nullable public Integer getDuration();

    /**
     * @param successStatus Success status code.
     */
    public default boolean isFailedButNotMuted(int successStatus) {
        return isFailedTest(successStatus) && !isMutedOrIgnored();
    }

    public default boolean isFailedTest(int successStatus) {
        return successStatus != status();
    }

    public boolean isFailedTest(IStringCompactor compactor);

    public default boolean isMutedOrIgnored() {
        return isMutedTest() || isIgnoredTest();
    }

    /**
     * For newer version- filtered log
     */
    public String getDetailsText();

    /**
     * @return Test global ID, can be used for references.
     */
    public Long getTestId();

    boolean isFailedButNotMuted(IStringCompactor compactor);

    public Boolean getIgnoredFlag();

    public Boolean getMutedFlag();

    public int getActualBuildId();
    public int idInBuild();
}
