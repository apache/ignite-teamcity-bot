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

package org.apache.ignite.ci.tcmodel.hist;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.ci.tcmodel.result.AbstractRef;

/**
 * Actual result of build execution from build history,
 * short version involved in snapshot dependencies and into build history list
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildRef extends AbstractRef {
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_SUCCESS = "SUCCESS";
    @XmlAttribute private Integer id;

    @XmlAttribute public String buildTypeId;

    @XmlAttribute public String branchName;

    @XmlAttribute public String status;

    public static final String STATE_FINISHED = "finished";

    public static final String STATE_RUNNING = "running";

    public static final String STATE_QUEUED = "queued";

    @XmlAttribute private String state;

    @XmlAttribute(name = "number") public String buildNumber;

    @XmlAttribute public Boolean defaultBranch;

    @XmlAttribute public Boolean composite;

    @XmlAttribute public String webUrl;

    /**
     * @return Build ID
     */
    public Integer getId() {
        return id;
    }

    public boolean isNotCancelled() {
        return !hasUnknownStatus();
    }

    private boolean hasUnknownStatus() {
        return STATUS_UNKNOWN.equals(status);
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * @return
     */
    public String suiteId() {
        return buildTypeId;
    }

    /**
     * @return
     */
    public boolean isFakeStub() {
        return getId() == null;
    }

    /**
     * @param id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return true if build is composite.
     */
    public boolean isComposite() {
        return composite != null && composite;
    }
}
