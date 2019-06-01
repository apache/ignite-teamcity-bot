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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.tcservice.model.changes.ChangesListRef;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * List of Projects available at TC.
 */
@XmlRootElement(name = "projects")
public class ProjectsList extends ChangesListRef {
    /** Projects. */
    @XmlElement(name = "project")
    private List<Project> projects;

    /** Count. */
    @XmlElement Integer count;

    /** {@inheritDoc} */
    @Override public String toString() {
        return "ProjectsList{" +
            "projects=" + projects +
            '}';
    }

    /** */
    @NonNull
    public List<Project> projects() {
        return projects == null ? Collections.emptyList() : Collections.unmodifiableList(projects);
    }
}
