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

package org.apache.ignite.ci.web.model;

import org.jetbrains.annotations.Nullable;

/**
 * Representation of TC Bot visa - comment in apache Jira ticket conversation which shows a list of tests which probably
 * failed because of committed changes(blockers) based on TC Run-All results.
 */
public class Visa {
    /** Determines Visa with no results and info. */
    public static final String EMPTY_VISA_STATUS = "emptyVisa";

    /** Message to show user when JIRA ticket was successfully commented by the Bot. */
    public static final String JIRA_COMMENTED = "JIRA commented.";

    /** Message to show user when requested run result targets were successfully commented by the Bot. */
    public static final String COMMENTED = "Run result commented.";

    /** Message to show user when some requested targets were not commented. */
    public static final String PARTIALLY_COMMENTED =
        "Run result partially commented - GitHub wasn't commented.";

    /** Message prefix to show user when comment was intentionally skipped. */
    public static final String COMMENT_SKIPPED = "Run result comment skipped:";

    /** */
    private static final String JIRA_COMMENTED_PREFIX = "JIRA ticket commented:";

    /** */
    private static final String JIRA_ALREADY_COMMENTED_PREFIX =
        "JIRA ticket already has a valid TCBot comment for this build:";

    /** */
    private static final String GITHUB_COMMENTED_PREFIX = "GitHub PR commented:";

    /** */
    private static final String GITHUB_ALREADY_COMMENTED_PREFIX =
        "GitHub PR already has a valid TCBot comment for this build:";

    /** Machine-readable result, independent from user-visible {@link #status}. */
    public enum Result {
        /** Legacy compacted entries did not store a result code. */
        UNKNOWN,

        /** Empty placeholder. */
        EMPTY,

        /** Comment flow is complete and observer can stop retrying. */
        SUCCESS,

        /** Comment flow failed and may be retried by observer. */
        FAILURE
    }

    /** */
    public final String status;

    /** Machine-readable result. */
    public final Result result;

    /** */
    @Nullable public final JiraCommentResponse jiraCommentRes;

    /** */
    public final int blockers;

    /**
     * @return instance of {@link Visa} with {@link #EMPTY_VISA_STATUS}
     */
    public static Visa emptyVisa() {
        return new Visa(EMPTY_VISA_STATUS, null, 0, Result.EMPTY);
    }

    /**
     * @param status User-visible status.
     * @param res JIRA response.
     * @param blockers Blockers count.
     */
    public static Visa success(String status, @Nullable JiraCommentResponse res, int blockers) {
        return new Visa(status, res, blockers, Result.SUCCESS);
    }

    /**
     * @param status User-visible status.
     */
    public static Visa failure(String status) {
        return new Visa(status, null, 0, Result.FAILURE);
    }

    /**
     * @param blockers Blockers count.
     * @return User-visible status for skipped result comment.
     */
    public static String commentSkipped(int blockers) {
        return COMMENT_SKIPPED + " " + blockers + " " + (blockers == 1 ? "blocker" : "blockers") + " found.";
    }

    /**
     * @param blockers Blockers count.
     */
    public static Visa skipped(int blockers) {
        return success(commentSkipped(blockers), null, blockers);
    }

    /** */
    public Visa(String status) {
        this(status, null, 0);
    }

    /** */
    public Visa(String status, @Nullable JiraCommentResponse res, Integer blockers) {
        this(status, res, blockers, legacyResult(status, res));
    }

    /** */
    public Visa(String status, @Nullable JiraCommentResponse res, Integer blockers, Result result) {
        this.status = status;
        this.jiraCommentRes = res;
        this.blockers = blockers;
        this.result = result;
    }

    /** */
    @Nullable public JiraCommentResponse getJiraCommentResponse() {
        return jiraCommentRes;
    }

    /** */
    @Nullable public int getBlockers() {
        return blockers;
    }

    /** */
    public boolean isSuccess() {
        return result == Result.SUCCESS;
    }

    /**
     * Compatibility only: old compacted Visa entries did not store {@link #result}.
     *
     * @param status User-visible status.
     * @param jiraCommentRes JIRA comment response.
     */
    static Result legacyResult(String status, @Nullable JiraCommentResponse jiraCommentRes) {
        if (status == null)
            return Result.FAILURE;

        if (EMPTY_VISA_STATUS.equals(status))
            return Result.EMPTY;

        if ((JIRA_COMMENTED.equals(status) && jiraCommentRes != null)
            || COMMENTED.equals(status)
            || (PARTIALLY_COMMENTED.equals(status) && jiraCommentRes != null)
            || ((status.startsWith(JIRA_COMMENTED_PREFIX) || status.startsWith(PARTIALLY_COMMENTED)) &&
                jiraCommentRes != null)
            || status.startsWith(JIRA_ALREADY_COMMENTED_PREFIX)
            || status.startsWith(GITHUB_COMMENTED_PREFIX)
            || status.startsWith(GITHUB_ALREADY_COMMENTED_PREFIX)
            || status.contains("; " + GITHUB_COMMENTED_PREFIX)
            || status.contains("; " + GITHUB_ALREADY_COMMENTED_PREFIX)
            || status.startsWith(COMMENT_SKIPPED))
            return Result.SUCCESS;

        return Result.FAILURE;
    }

    /** */
    public boolean isEmpty() {
        return result == Result.EMPTY;
    }

    /** */
    @Override public String toString() {
        return status;
    }
}
