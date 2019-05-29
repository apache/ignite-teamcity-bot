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

package org.apache.ignite.ci.issue;

import com.google.common.collect.Lists;
import java.util.ArrayList;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunStatus.RES_CRITICAL_FAILURE;
import static org.apache.ignite.ci.teamcity.ignited.runhist.RunStatus.RES_FAILURE;
import static org.apache.ignite.ci.teamcity.ignited.runhist.RunStatus.RES_OK;
import static org.apache.ignite.ci.teamcity.ignited.runhist.RunStatus.RES_OK_OR_FAILURE;

public class EventTemplates {
    private static final int OK = RES_OK.getCode();
    private static final int FAIL = RES_FAILURE.getCode();

    public static final EventTemplate newFailure = new EventTemplate(
            new int[]{OK, OK, OK, OK, OK},
            new int[]{FAIL, FAIL, FAIL, FAIL}
    );

    public static final EventTemplate fixOfFailure = new EventTemplate(
            new int[]{FAIL, FAIL, FAIL},
            new int[]{OK, OK, OK, OK, OK}
    );

    public static final EventTemplate newCriticalFailure = new EventTemplate(
            new int[]{RES_OK_OR_FAILURE.getCode(), RES_OK_OR_FAILURE.getCode(), RES_OK_OR_FAILURE.getCode(), RES_OK_OR_FAILURE.getCode(), RES_OK_OR_FAILURE.getCode()},
            new int[]{RES_CRITICAL_FAILURE.getCode(), RES_CRITICAL_FAILURE.getCode(), RES_CRITICAL_FAILURE.getCode(), RES_CRITICAL_FAILURE.getCode()}
    );

    public static final EventTemplate newContributedTestFailure = new EventTemplate(
            new int[]{},
            new int[]{FAIL, FAIL, FAIL, FAIL}
    ).setShouldBeFirst(true);

    public static final EventTemplate newFailureForFlakyTest = new EventTemplate(
            new int[]{OK, OK, OK, OK, OK},
            new int[]{FAIL, FAIL, FAIL,
                    FAIL, FAIL, FAIL,
                    FAIL, FAIL}
    );

    public static ArrayList<EventTemplate> templates;

    static {
        templates = Lists.newArrayList(newFailure, newCriticalFailure, fixOfFailure,
            newContributedTestFailure, newFailureForFlakyTest);
    }
}
