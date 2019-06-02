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

import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of {@link Visa} with compacted properties for more effective cache storing.
 */
public class CompactVisa {
    /** */
    public final int status;

    /** */
    @Nullable public final JiraCommentResponse jiraCommentRes;

    /** */
    public final int blockers;

    /** */
    public CompactVisa(Visa visa, IStringCompactor strCompactor) {
        this.status = strCompactor.getStringId(visa.status);
        this.blockers = visa.blockers;
        this.jiraCommentRes = visa.getJiraCommentResponse();
    }

    /** */
    public Visa toVisa(IStringCompactor strCompactor) {
        return new Visa(strCompactor.getStringFromId(status), jiraCommentRes, blockers);
    }
}
