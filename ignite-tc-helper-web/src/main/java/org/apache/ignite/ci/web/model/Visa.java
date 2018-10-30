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

import java.util.List;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class Visa {
    /** */
    private final String status;

    /** */
    @Nullable private final JiraCommentResponse jiraCommentResponse;

    /** */
    @Nullable private final List<SuiteCurrentStatus> suitesStatuses;

    /** */
    @Nullable public String getStatus() {
        return status;
    }

    /** */
    public Visa(String status) {
        this.status = status;
        this.jiraCommentResponse = null;
        this.suitesStatuses = null;
    }

    /** */
    public Visa(String status, JiraCommentResponse response, List<SuiteCurrentStatus> suitesStasuses) {
        this.status = status;
        this.jiraCommentResponse = response;
        this.suitesStatuses = suitesStasuses;
    }

    /** */
    @Nullable public JiraCommentResponse getJiraCommentResponse() {
        return jiraCommentResponse;
    }

    /** */
    @Nullable public List<SuiteCurrentStatus> getSuitesStatuses() {
        return suitesStatuses;
    }

    /** */
    public boolean isSuccess() {
        return IJiraIntegration.JIRA_COMMENTED.equals(status) 
            && jiraCommentResponse != null 
            && suitesStatuses != null;
    }

    /** */
    @Override public String toString() {
        return status;
    }
}
