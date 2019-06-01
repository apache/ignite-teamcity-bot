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

package org.apache.ignite.tcservice.model.vcs;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Revision {
    @XmlAttribute(name = "version")
    private String version;

    @XmlAttribute(name = "vcsBranchName")
    private String vcsBranchName;

    @XmlElement(name = "vcs-root-instance")
    private VcsRootInstance vcsRootInstance;

    public Revision() {
    }

    /**
     *
     */
    public String version() {
        return version;
    }

    /**
     *
     */
    public String vcsBranchName() {
        return vcsBranchName;
    }

    /**
     * @param ver Version.
     */
    public Revision version(String ver) {
        this.version = ver;

        return this;
    }

    /**
     *
     */
    public VcsRootInstance vcsRootInstance() {
        return vcsRootInstance;
    }

    public Revision vcsBranchName(String vcsBranchName) {
        this.vcsBranchName = vcsBranchName;

        return this;
    }

    public Revision vcsRootInstance(VcsRootInstance vcsRootInstance) {
        this.vcsRootInstance = vcsRootInstance;

        return this;
    }
}
