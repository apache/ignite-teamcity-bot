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
import org.apache.ignite.ci.analysis.RunStat;

public class EventTemplates {
    public static final EventTemplate newFailure = new EventTemplate(
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK},
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE}
    );

    public static final EventTemplate fixOfFailure = new EventTemplate(
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE},
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK}
    );

    public static final EventTemplate newCriticalFailure = new EventTemplate(
            new int[]{RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE,
                    RunStat.RES_OK_OR_FAILURE, RunStat.RES_OK_OR_FAILURE},
            new int[]{RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE,
                    RunStat.RES_CRITICAL_FAILURE, RunStat.RES_CRITICAL_FAILURE}
    );

    public static final EventTemplate newContributedTestFailure = new EventTemplate(
            new int[]{},
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE}
    ).setShouldBeFirst(true);

    public static final EventTemplate newFailureForFlakyTest = new EventTemplate(
            new int[]{RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK, RunStat.RES_OK},
            new int[]{RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE,
                    RunStat.RES_FAILURE, RunStat.RES_FAILURE, RunStat.RES_FAILURE,
                    RunStat.RES_FAILURE, RunStat.RES_FAILURE}
    );

    public static ArrayList<EventTemplate> templates;

    static {
        templates = Lists.newArrayList(newFailure, newCriticalFailure, fixOfFailure,
            newContributedTestFailure, newFailureForFlakyTest);
    }
}
