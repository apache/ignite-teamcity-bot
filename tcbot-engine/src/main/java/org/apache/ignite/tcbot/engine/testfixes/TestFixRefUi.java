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

package org.apache.ignite.tcbot.engine.testfixes;

import javax.annotation.Nullable;

/**
 * Link to a ticket or PR mentioning a test/suite fix.
 */
@SuppressWarnings("PublicField")
public class TestFixRefUi {
    /** Matched test or suite name. */
    public String entityName;

    /** Suite id. */
    @Nullable public String suiteId;

    /** Suite display name. */
    @Nullable public String suiteName;

    /** Test display name. */
    @Nullable public String testName;

    /** Tracked branch used to resolve this match. */
    @Nullable public String trackedBranch;

    /** Current tracked branch status URL. */
    @Nullable public String currentStatusUrl;

    /** Source type: jira or github. */
    public String sourceType;

    /** Short source text, for example IGNITE-12345 or PR #123. */
    public String text;

    /** Source URL. */
    public String url;

    /** Source title. */
    @Nullable public String title;

    /** Source status. */
    @Nullable public String status;

    /** Author name/login. */
    @Nullable public String author;

    /** Author URL. */
    @Nullable public String authorUrl;

    /** Author avatar URL. */
    @Nullable public String authorAvatarUrl;

    /** Updated date. */
    @Nullable public String updatedDate;

    /** Closed/resolved date. */
    @Nullable public String closedDate;

    /** Closing or best-known fix commit URL. */
    @Nullable public String commitUrl;

    /** Closing or best-known fix commit text. */
    @Nullable public String commitText;
}
