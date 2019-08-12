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

package org.apache.ignite.tcbot.engine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.internal.util.typedef.internal.U;

import javax.annotation.Nullable;

/**
 * Detailed status model for overall response.
 *
 * Summary of failures from all servers. UI model, so it contains public fields.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class DsSummaryUi extends UpdateInfo {
    /** Servers (Services) and their chain results. */
    public List<DsChainUi> servers = new ArrayList<>();

    public Integer failedTests;

    /** Count of suites with critical build problems found */
    public Integer failedToFinish;

    /** Tracked branch ID. */
    @Nullable
    private String trackedBranch;

    public DsSummaryUi addChainOnServer(DsChainUi chainStatus) {
        servers.add(chainStatus);

        if (chainStatus.failedToFinish != null) {
            if (failedToFinish == null)
                failedToFinish = 0;

            failedToFinish += chainStatus.failedToFinish;
        }

        if (chainStatus.failedTests != null) {
            if (failedTests == null)
                failedTests = 0;

            failedTests += chainStatus.failedTests;
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DsSummaryUi summary = (DsSummaryUi)o;
        return Objects.equals(servers, summary.servers) &&
            Objects.equals(failedTests, summary.failedTests) &&
            Objects.equals(failedToFinish, summary.failedToFinish) &&
            Objects.equals(trackedBranch, summary.trackedBranch);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(servers, failedTests, failedToFinish, trackedBranch);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        StringBuilder builder = new StringBuilder();

        servers.forEach(
            s -> {
                if (s != null)
                    builder.append(s.toString());
            }
        );

        return builder.toString();
    }

    public void setTrackedBranch(String trackedBranch) {
        this.trackedBranch = trackedBranch;
    }

    @Nullable
    public String getTrackedBranch() {
        return trackedBranch;
    }
}
