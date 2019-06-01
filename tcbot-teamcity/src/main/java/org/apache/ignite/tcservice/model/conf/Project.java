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

package org.apache.ignite.tcservice.model.conf;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Content of poject
 */
@XmlRootElement(name = "project")
@XmlAccessorType(XmlAccessType.FIELD)
public class Project {
    /** Id. */
    @XmlAttribute public String id;

    /** Name. */
    @XmlAttribute(name = "name")
    private String name;

    /** Build types. */
    @XmlElementWrapper(name = "buildTypes")
    @XmlElement(name = "buildType")
    private List<BuildType> buildTypes;

    /**
     * @return List of project's build types or an empty list if there is no build types presented.
     */
    public List<BuildType> getBuildTypesNonNull() {
        return buildTypes == null ? Collections.emptyList() : buildTypes;
    }

    /** */
    public void name(String name) {
        this.name = name;
    }

    /** */
    public String name() {
        return name;
    }

    /** */
    public String id() {
        return id;
    }
}

