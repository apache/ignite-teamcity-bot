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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.ci.TcHelper;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class Visa {
    /** */
    @Nullable private String status;

    /** */
    @Nullable private JiraCommentResponse jiraCommentResponse;

    /** */
    @Nullable private List<SuiteCurrentStatus> suitesStatuses;

    /** */
    @Nullable public String getStatus() {
        return status;
    }

    /** */
    public Visa setStatus(@Nullable String status) {
        this.status = status;

        return this;
    }

    /** */
    @Nullable public JiraCommentResponse getJiraCommentResponse() {
        return jiraCommentResponse;
    }

    /** */
    public Visa setJiraCommentResponse(@Nullable JiraCommentResponse jiraCommentResponse) {
        this.jiraCommentResponse = jiraCommentResponse;

        return this;
    }

    /** */
    @Nullable public List<SuiteCurrentStatus> getSuitesStatuses() {
        return suitesStatuses;
    }

    /** */
    public Visa setSuitesStatuses(@Nullable List<SuiteCurrentStatus> suitesStatuses) {
        this.suitesStatuses = suitesStatuses;

        return this;
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
