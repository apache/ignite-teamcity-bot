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

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcbot.common.util.TimeUtil;

/**
 * Issue used both for saving into DB and in UI (in issue history).
 * Issue is any detected failure of test or suite.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
@Persisted
public class Issue {
    /** Type code. Null of older versions of issue */
    @Nullable
    public String type;

    /** Display type. for issue. Kept for backward compatibilty with older records without type code. */
    private String displayType;

    public double flakyRate;

    /** Branch alias */
    @Nullable
    public String trackedBranchName;

    public IssueKey issueKey;

    public List<ChangeUi> changes = new ArrayList<>();

    public Set<String> addressNotified = new TreeSet<>();

    @Nullable
    public String webUrl;

    @Nullable
    public String displayName;

    /** Build start timestamp. Builds which is older that 10 days not notified. */
    @Nullable public Long buildStartTs;

    /** Detected timestamp. */
    @Nullable public Long detectedTs;

    /** Set of build tags detected. */
    public Set<String> buildTags = new TreeSet<>();

    /** Statistics of subscribers for this issue. Filled accordignly recent update. */
    public Map<String, Object> stat = new HashMap<>();

    /** Notification failed: Map from address to exception text */
    @Nullable public Map<String, String> notificationFailed = new HashMap<>();

    public int notificationRetry = 0;

    public Issue(IssueKey issueKey, IssueType type,
        @Nullable Long buildStartTs) {
        this.issueKey = issueKey;
        this.detectedTs = System.currentTimeMillis();
        this.type = type.code();
        this.displayType = type.displayName();
        this.buildStartTs = buildStartTs;
    }

    public void addChange(String username, String webUrl) {
        changes.add(new ChangeUi(username, webUrl));
    }

    public String toHtml(boolean includeChangesInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append(displayType);

        if (trackedBranchName != null)
            sb.append(" in ").append(trackedBranchName);

        sb.append(" ");

        if (webUrl != null)
            sb.append("<a href='").append(webUrl).append("'>").append(getDisplayName()).append("</a>");
        else
            sb.append(getDisplayName());


        if(includeChangesInfo) {
            if (changes.isEmpty())
                sb.append(" No changes in the build");
            else {

                sb.append(" Changes may lead to failure were done by ");

                for (Iterator<ChangeUi> iter = changes.iterator(); iter.hasNext(); ) {
                    ChangeUi next = iter.next();
                    sb.append(next.toHtml());

                    if (iter.hasNext())
                        sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    public IssueKey issueKey() {
        return issueKey;
    }

    public String toSlackMarkup(boolean includeChangesInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append(displayType);

        if (trackedBranchName != null)
            sb.append(" in ").append(trackedBranchName);

        sb.append(" ");

        String name = getDisplayName();

        if (webUrl != null)
            sb.append("<").append(webUrl).append("|").append(name).append(">");
        else
            sb.append(name);

        if(includeChangesInfo) {
            if (changes.isEmpty())
                sb.append("\n No changes in the build");
            else {

                sb.append("\n Changes may led to failure were done by ");

                for (Iterator<ChangeUi> iter = changes.iterator(); iter.hasNext(); ) {
                    ChangeUi next = iter.next();
                    sb.append(next.toSlackMarkup());

                    if (iter.hasNext())
                        sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    public String getDisplayName() {
        if (displayName == null)
            return issueKey.getTestOrBuildName();

        return displayName;
    }

    /**
     * @return Set of build tags detected.
     */
    @Nonnull public Set<String> buildTags() {
        return buildTags == null && buildTags.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(buildTags);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        String tsStart = buildStartTs == null ? null : TimeUtil.timestampToDateTimePrintable(buildStartTs);
        String tsDetect = detectedTs == null ? null : TimeUtil.timestampToDateTimePrintable(detectedTs);
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("displayType", displayType)
            .add("trackedBranchName", trackedBranchName)
            .add("issueKey", issueKey)
            .add("changes", changes)
            .add("addressNotified", addressNotified)
            .add("webUrl", webUrl)
            .add("displayName", displayName)
            .add("buildStartTs", tsStart)
            .add("detectedTs", tsDetect)
            .toString();
    }

    public String toPlainText(boolean includeChangesInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append(displayType);

        if (trackedBranchName != null)
            sb.append(" in ").append(trackedBranchName);

        sb.append(" ");

        if (webUrl != null)
            sb.append("").append(getDisplayName()).append(" ").append(webUrl).append("");
        else
            sb.append(getDisplayName());

        sb.append("\n");

        if(includeChangesInfo) {
            if (changes.isEmpty())
                sb.append(" No changes in the build");
            else {

                sb.append(" Changes may lead to failure were done by \n");

                for (Iterator<ChangeUi> iter = changes.iterator(); iter.hasNext(); ) {
                    ChangeUi next = iter.next();

                    sb.append("\t - ");
                    sb.append(next.toPlainText());

                    if (iter.hasNext())
                        sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public String type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Issue issue = (Issue)o;
        return notificationRetry == issue.notificationRetry &&
            Objects.equals(type, issue.type) &&
            Objects.equals(displayType, issue.displayType) &&
            Objects.equals(trackedBranchName, issue.trackedBranchName) &&
            Objects.equals(issueKey, issue.issueKey) &&
            Objects.equals(changes, issue.changes) &&
            Objects.equals(addressNotified, issue.addressNotified) &&
            Objects.equals(webUrl, issue.webUrl) &&
            Objects.equals(displayName, issue.displayName) &&
            Objects.equals(buildStartTs, issue.buildStartTs) &&
            Objects.equals(detectedTs, issue.detectedTs) &&
            Objects.equals(buildTags, issue.buildTags) &&
            Objects.equals(stat, issue.stat) &&
            Objects.equals(notificationFailed, issue.notificationFailed);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(type, displayType, trackedBranchName, issueKey, changes, addressNotified, webUrl, displayName, buildStartTs, detectedTs, buildTags, stat, notificationFailed, notificationRetry);
    }
}
