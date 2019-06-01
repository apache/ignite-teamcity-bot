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

package org.apache.ignite.tcservice.model.result.tests;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Test occurrence. Can be provided by build as list of occurrences.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class TestOccurrence {
    /** Status success. */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** Status failure. */
    public static final String STATUS_FAILURE = "FAILURE";

    @XmlAttribute
    private String id;

    @XmlAttribute
    public String name;
    @XmlAttribute
    public String status;
    @XmlAttribute
    public Integer duration;
    @XmlAttribute
    public String href;

    @XmlAttribute
    public Boolean muted;
    @XmlAttribute
    public Boolean currentlyMuted;
    @XmlAttribute
    public Boolean currentlyInvestigated;
    @XmlAttribute
    public Boolean ignored;

    public String getName() {
        return name;
    }

    public boolean isFailedTest() {
        return !STATUS_SUCCESS.equals(status);
    }

    public boolean isMutedTest() {
        return (muted != null && muted) || (currentlyMuted != null && currentlyMuted);
    }

    public boolean isIgnoredTest() {
        return (ignored != null && ignored);
    }

    public boolean isNotMutedOrIgnoredTest() {
        return !isMutedTest() && !isIgnoredTest();
    }

    public boolean isInvestigated() {
        return (currentlyInvestigated != null && currentlyInvestigated);
    }

    public boolean isFailedButNotMuted() {
        return isFailedTest() && !(isMutedTest() || isIgnoredTest());
    }

    /**
     * @return Test in build occurrence id, something like: 'id:15666,build:(id:1093907)'
     */
    public String getId() {
        return id;
    }

    public TestOccurrence setId(String id) {
        this.id = id;

        return this;
    }

    public TestOccurrence setStatus(String status) {
        this.status = status;

        return this;
    }

    public void id(String s) {
        id = s;
    }
}
