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

package org.apache.ignite.ci.tcmodel.result;

import com.google.common.base.Strings;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.changes.ChangesListRef;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.jetbrains.annotations.NotNull;

/**
 * Build from history with test and problems references
 */
@XmlRootElement(name = "build")
@XmlAccessorType(XmlAccessType.FIELD)
public class Build extends BuildRef implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;
    @XmlElement(name = "buildType") BuildType buildType;

    @XmlElement public String queuedDate;
    @XmlElement public String startDate;
    @XmlElement public String finishDate;

    @XmlElement(name = "build")
    @XmlElementWrapper(name = "snapshot-dependencies")
    private List<BuildRef> snapshotDependencies;

    @XmlElement(name = "problemOccurrences") public ProblemOccurrencesRef problemOccurrences;

    @XmlElement(name = "testOccurrences") public TestOccurrencesRef testOccurrences;

    @XmlElement(name = "statistics") public StatisticsRef statisticsRef;

    @XmlElement(name = "relatedIssues") public RelatedIssuesRef relatedIssuesRef;

    /** Changes not included into build.*/
    @XmlElement(name = "lastChanges") public ChangesList lastChanges;

    /** Changes included into build.*/
    @XmlElement(name = "changes") public ChangesListRef changesRef;

    /** Information about build triggering. */
    @XmlElement(name = "triggered") private Triggered triggered;

    @SuppressWarnings("FieldCanBeLocal") public Integer _version = LATEST_VERSION;

    @NotNull public static Build createFakeStub() {
        return new Build();
    }

    public List<BuildRef> getSnapshotDependenciesNonNull() {
        return snapshotDependencies == null ? Collections.emptyList() : snapshotDependencies;
    }

    public String suiteName() {
        return buildType == null ? null : buildType.getName();
    }

    public String getFinishDateDdMmYyyy() {
        Date parse = getFinishDate();
        return new SimpleDateFormat("dd.MM.yyyy").format(parse);
    }

    public Date getFinishDate() {
        return getDate(finishDate);
    }

    public Date getStartDate() {
        return getDate(startDate);
    }

    private Date getDate(String date) {
        try {
            if (date == null)
                return null;
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
            return f.parse(date);
        }
        catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasFinishDate() {
        return !Strings.isNullOrEmpty(finishDate);
    }

    public BuildType getBuildType() {
        return buildType;
    }

    @Override public int version() {
        return _version == null ? 0 : _version;
    }

    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    /**
     * @return Information about build triggering.
     */
    public Triggered getTriggered() {
        return triggered;
    }

    /**
     * @param triggered Information about build triggering.
     */
    public void setTriggered(Triggered triggered) {
        this.triggered = triggered;
    }
}