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

package org.apache.ignite.ci.jira;

/**
 * See example of GSON here
 * https://issues.apache.org/jira/rest/api/2/issue/IGNITE-123
 */
public class Ticket {
    /** Id. */
    public long id;

    /** Ticket full name like "IGNITE-123". */
    public String key;

    /** Fields. */
    public Fields fields;

    /**
     * @param ticketTemplate Ticket name template.
     * @return Ignite id (like 123 in IGNITE-123).
     */
    public int igniteId(String ticketTemplate) {
        return Integer.valueOf(key.substring(ticketTemplate.length()));
    }

    /**
     * @return Ticket status (open, resolved, etc);
     */
    public String status() {
        return fields.status.name;
    }
}