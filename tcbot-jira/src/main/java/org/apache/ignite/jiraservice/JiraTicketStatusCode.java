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
package org.apache.ignite.jiraservice;

/**
 * Enumerates well-known JIRA issue statuses and its internal IDs.
 */
public enum JiraTicketStatusCode {
    OPEN(1, "Open"),
    IN_PROGRESS(3, "In Progress"),
    REOPENED(4, "Reopened"),
    RESOLVED(5, "Resolved"),
    CLOSED(6, "Closed"),
    BACKLOG(10010, "Backlog"),
    PATCH_AVAILABLE(10012, "Patch Available"),
    PENDING(10402, "Pending"),
    PATCH_REVIEWED(10800, "Patch Reviewed");

    JiraTicketStatusCode(int id, String name) {
       this.id = id;
       this.name = name;
    }

    private final int id;

    private final String name;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * @param id Status ID.
     * @return Resolved status or <code>null</code> if status not found.
     */
    public static JiraTicketStatusCode fromId(int id) {
        for (JiraTicketStatusCode code : values()) {
            if (code.id == id)
                return code;
        }

        return null;
    }

    /**
     * @return Text representation of given status code.
     */
    public static String text(JiraTicketStatusCode statusCode) {
        if (statusCode == null)
            return "<unknown>";

        return statusCode.getName();
    }

    /**
     * Checks if ticket relates to some Active (In progress/Patch Available) Contribution.
     */
    public static boolean isActiveContribution(JiraTicketStatusCode statusCode) {
        if (statusCode == null)
            return false;

        return JiraTicketStatusCode.PATCH_AVAILABLE == statusCode ||
            JiraTicketStatusCode.IN_PROGRESS == statusCode ||
            JiraTicketStatusCode.OPEN == statusCode ||
            JiraTicketStatusCode.BACKLOG == statusCode ||
            JiraTicketStatusCode.REOPENED == statusCode ||
            JiraTicketStatusCode.PATCH_REVIEWED == statusCode;
    }
}
