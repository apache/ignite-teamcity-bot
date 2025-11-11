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

package org.apache.ignite.jiraservice.v3;

import com.google.common.base.MoreObjects;
import java.util.List;
import org.apache.ignite.jiraservice.IFields;
import org.apache.ignite.jiraservice.Status;

/**
 * The Atlassian Document Format (ADF) represents rich text stored in Atlassian products.
 * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/
 */
public class FieldsV3 implements IFields {
    /** Ticket status. */
    private Status status;

    /** Summary. */
    private String summary;

    /** Customfield 11050. */
    private String customfield_11050;

    /** Description. */
    private Description description;

    /** {@inheritDoc} */
    @Override public Status status() {
        return status;
    }

    /** {@inheritDoc} */
    @Override public String summary() {
        return summary;
    }

    /** {@inheritDoc} */
    @Override public String igniteLink() {
        return customfield_11050;
    }

    /** {@inheritDoc} */
    @Override public String description() {
        StringBuilder sb = new StringBuilder();

        if (description != null) {
            description.content.forEach(item -> processContentItem(item, sb));
        }

        return sb.toString();
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("status", status)
            .add("summary", summary)
            .add("customfield_11050", customfield_11050)
            .toString();
    }

    private static void processContentItem(ContentItem item, StringBuilder sb) {
        if (item.text != null && !item.text.isEmpty())
            sb.append(item.text);

        if (item.content != null)
            item.content.forEach(i -> processContentItem(i, sb));

        if ("paragraph".equalsIgnoreCase(item.type))
            sb.append(System.lineSeparator());
    }

    static class Description {
        // Defines the version of ADF used in this representation.
        int version;

        // Element type.
        String type;

        List<ContentItem> content;
    }

    static class ContentItem {
        String type;
        String text;
        List<ContentItem> content;
    }
}
