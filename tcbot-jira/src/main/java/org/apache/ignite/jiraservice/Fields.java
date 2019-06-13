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

import com.google.common.base.MoreObjects;

/**
 *
 */
public class Fields {
    /** Ticket status. */
    public Status status;

    /** Summary. */
    public String summary;

    /** Customfield 11050. */
    public String customfield_11050;

    /** Description. */
    public String description;

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("status", status)
            .add("summary", summary)
            .add("customfield_11050", customfield_11050)
            .toString();
    }
}