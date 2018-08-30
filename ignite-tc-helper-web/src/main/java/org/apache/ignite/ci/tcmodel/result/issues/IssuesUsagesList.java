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

package org.apache.ignite.ci.tcmodel.result.issues;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * List of build's related issues from TC.
 *
 * See example of XML, e.g. here
 * https://ci.ignite.apache.org/app/rest/latest/builds/id:1694977/relatedIssues
 */
@XmlRootElement(name = "issuesUsages")
public class IssuesUsagesList {
    @XmlElement(name = "issueUsage")
    private List<IssueUsage> issuesUsages;

    @XmlElement Integer count;

    @XmlElement String href;

    public List<IssueUsage> getIssuesUsagesNonNull() {
        return issuesUsages == null ? Collections.emptyList() : issuesUsages;
    }

    @Override public String toString() {
        return "IssuesUsagesList{" +
            "issuesUsages=" + issuesUsages +
            '}';
    }
}
