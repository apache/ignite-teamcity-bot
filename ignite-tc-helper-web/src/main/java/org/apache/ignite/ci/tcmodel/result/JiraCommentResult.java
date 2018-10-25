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

package org.apache.ignite.ci.tcmodel.result;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.TcHelper;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class JiraCommentResult {
    /** */
    @Nullable private String result;

    /** */
    @Nullable private JiraCommentResponse response;

    /** */
    @Nullable private List<SuiteCurrentStatus> suitesStatus = new ArrayList<>();

    /** */
    @Nullable public List<SuiteCurrentStatus> getSuitesStatus() {
        return suitesStatus;
    }

    /** */
    public JiraCommentResult setSuitesStatus(List<SuiteCurrentStatus> suiteCurrentStatuses) {
        this.suitesStatus = suiteCurrentStatuses;

        return this;
    }

    /** */
    public JiraCommentResult setResult(String result) {
        this.result = result;

        return this;
    }

    /** */
    @Nullable public String getResult() {
        return result;
    }

    /** */
    @Nullable public JiraCommentResponse getResponse() {
        return response;
    }

    /** */
    public JiraCommentResult setResponse(JiraCommentResponse response) {
        this.response = response;

        return this;
    }

    /** */
    public boolean isSuccess() {
        return TcHelper.JIRA_COMMENTED.equals(result);
    }

    /** */
    @Override public String toString() {
        return result;
    }
}
