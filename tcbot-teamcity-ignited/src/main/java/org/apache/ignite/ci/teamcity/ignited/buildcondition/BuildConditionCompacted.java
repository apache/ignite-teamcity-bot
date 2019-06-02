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

package org.apache.ignite.ci.teamcity.ignited.buildcondition;

import java.util.Date;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;

@Persisted
public class BuildConditionCompacted {
    /** Build id. */
    int buildId = -1;

    /** Username. */
    int username = -1;

    /** Is valid. */
    int isValid = -1;

    /** Date. */
    long date = -1;

    /** Field. */
    int field = -1;

    /**
     * Default constructor.
     */
    public BuildConditionCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param cond Build condition.
     */
    public BuildConditionCompacted(IStringCompactor compactor, BuildCondition cond) {
        buildId = cond.buildId;
        username = compactor.getStringId(cond.username);
        isValid = cond.isValid ? 1 : 0;
        date = cond.date.getTime();
        field = compactor.getStringId(cond.field);
    }

    /**
     * @param compactor Compactor.
     */
    public BuildCondition toBuildCondition(IStringCompactor compactor) {
        BuildCondition cond = new BuildCondition();

        cond.buildId = buildId;
        cond.isValid = isValid == 1;
        cond.date = new Date(date);
        cond.username = compactor.getStringFromId(username);
        cond.field = compactor.getStringFromId(field);

        return cond;
    }
}
