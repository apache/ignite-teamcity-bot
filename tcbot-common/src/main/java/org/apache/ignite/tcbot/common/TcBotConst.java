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

package org.apache.ignite.tcbot.common;

/**
 * TC Bot constants: contains magic numbers for project.
 *
 * Usually it is a practically set up rules, heuristics used by the bot.
 */
public class TcBotConst {
    /** Max days to keep test invocatoin data in run statistics: affects Bot Visa. */
    public static final int HISTORY_MAX_DAYS = 21;

    /** Absulte Max days to keep track about build existence, 42 */
    public static final int BUILD_MAX_DAYS = HISTORY_MAX_DAYS * 2;

    /** History collection process: build id per server ID border days. */
    public static final int HISTORY_BUILD_ID_BORDER_DAYS = HISTORY_MAX_DAYS + 2;

    /** Notify about failure: max build age days (since build start time). */
    public static final int NOTIFY_MAX_AGE_SINCE_START_DAYS = HISTORY_MAX_DAYS / 2;

    /**   */
    public static final int NOTIFY_MAX_AGE_SINCE_DETECT_HOURS = 2;

    /**   */
    public static final int NOTIFY_MAX_AGE_SINCE_DETECT_FOR_NOTIFIED_ISSUE_HOURS = 24;

    /** Flakyness status change border: Count of test change status before considered as flaky. */
    public static final int FLAKYNESS_STATUS_CHANGE_BORDER = 1;

    /** Non flaky test failure rate: less that this failure rate in base branch is still blocker border, percents. */
    public static final double NON_FLAKY_TEST_FAIL_RATE_BLOCKER_BORDER_PERCENTS = 4.;
}
