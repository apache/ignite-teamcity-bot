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

package org.apache.ignite.tcservice.model.hist;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** List of builds from build history */
@XmlRootElement(name = "builds")
@XmlAccessorType(XmlAccessType.FIELD)
public class Builds {
    @XmlAttribute
    private String nextHref;

    @XmlAttribute
    private Integer count;

    @XmlElement(name = "build")
    private List<BuildRef> builds;

    public List<BuildRef> getBuildsNonNull() {
        return builds == null ? Collections.emptyList() : builds;
    }

    public String nextHref() {
        return nextHref;
    }

    public void count(int count) {
        this.count = count;
    }

    public void nextHref(String nextHref) {
        this.nextHref = nextHref;
    }

    public void builds(List<BuildRef> list) {
        this.builds = list;
    }
}
