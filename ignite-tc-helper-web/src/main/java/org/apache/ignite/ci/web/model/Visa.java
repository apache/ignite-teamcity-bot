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

import org.apache.ignite.ci.jira.IJiraIntegration;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class Visa {
    /** */
    public static final String EMPTY_VISA_STATUS = "emptyVisa";

    /** */
    public final String status;

    /** */
    @Nullable public final JiraCommentResponse jiraCommentRes;

    /** */
    public final int blockers;

    /** */
    public static Visa emptyVisa() {
        return new Visa(EMPTY_VISA_STATUS);
    }

    /** */
    public Visa(String status) {
        this.status = status;
        this.jiraCommentRes = null;
        this.blockers = 0;
    }

    /** */
    public Visa(String status, JiraCommentResponse res, Integer blockers) {
        this.status = status;
        this.jiraCommentRes = res;
        this.blockers = blockers;
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
        return IJiraIntegration.JIRA_COMMENTED.equals(status)
            && jiraCommentRes != null;
    }

    /** */
    public boolean isEmpty() {
        return Visa.EMPTY_VISA_STATUS.equals(status);
    }


    /** */
    @Override public String toString() {
        return status;
    }
}
