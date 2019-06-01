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

package org.apache.ignite.tcservice.model.result;

import com.google.common.base.Strings;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import org.apache.ignite.tcservice.model.changes.ChangesListRef;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.conf.bt.Parameters;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.vcs.Revision;
import org.apache.ignite.tcservice.model.vcs.Revisions;
import org.checkerframework.checker.nullness.qual.NonNull;

import static org.apache.ignite.tcbot.common.exeption.ExceptionUtil.propagateException;

/**
 * Build from history with test and problems references
 */
@XmlRootElement(name = "build")
@XmlAccessorType(XmlAccessType.FIELD)
public class Build extends BuildRef {
    /** Format local. */
    @XmlTransient private static ThreadLocal<SimpleDateFormat> fmtLoc
        = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd'T'HHmmssZ"));

    @XmlElement(name = "buildType") private BuildType buildType;

    @XmlElement public String queuedDate;
    @XmlElement private String startDate;
    @XmlElement private String finishDate;

    @XmlElement(name = "build")
    @XmlElementWrapper(name = "snapshot-dependencies")
    private List<BuildRef> snapshotDependencies;

    @XmlElement(name = "problemOccurrences") public ProblemOccurrencesRef problemOccurrences;

    @XmlElement(name = "testOccurrences") public TestOccurrencesRef testOccurrences;

    @XmlElement(name = "statistics") public StatisticsRef statisticsRef;

    /** Changes included into build. */
    @XmlElement(name = "changes") public ChangesListRef changesRef;

    /** Information about build triggering. */
    @XmlElement(name = "triggered") private Triggered triggered;

    @XmlElement(name = "revisions") private Revisions revisions;

    /** Build parameters. */
    @Nullable
    @XmlElement(name = "properties") private Parameters properties;

    @NonNull  public static Build createFakeStub() {
        return new Build();
    }

    public List<BuildRef> getSnapshotDependenciesNonNull() {
        return snapshotDependencies == null ? Collections.emptyList() : snapshotDependencies;
    }

    public String suiteName() {
        return buildType == null ? null : buildType.getName();
    }

    /**
     *
     */
    public Date getFinishDate() {
        return getDate(finishDate);
    }

    /**
     *
     */
    public Date getStartDate() {
        return getDate(startDate);
    }

    /**
     * @param ts Timestamp.
     */
    public void setStartDateTs(long ts) {
        startDate = ts < 0 ? null : fmtLoc.get().format(new Date(ts));
    }

    /**
     * @param ts Timestamp.
     */
    public void setFinishDateTs(long ts) {
        finishDate = ts < 0 ? null : fmtLoc.get().format(new Date(ts));
    }


    /**
     *
     */
    public Date getQueuedDate() {
        return getDate(queuedDate);
    }

    /**
     * @param ts Timestamp.
     */
    public void setQueuedDateTs(long ts) {
        queuedDate = ts < 0 ? null : fmtLoc.get().format(new Date(ts));
    }

    /**
     * @param date Date as string.
     */
    private Date getDate(String date) {
        try {
            return date == null ? null : fmtLoc.get().parse(date);
        }
        catch (ParseException e) {
            throw propagateException(e);
        }
    }

    public boolean hasFinishDate() {
        return !Strings.isNullOrEmpty(finishDate);
    }

    public BuildType getBuildType() {
        return buildType;
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

    /**
     * @param type Type.
     */
    public void setBuildType(BuildType type) {
        buildType = type;
    }

    public void snapshotDependencies(List<BuildRef> dependencies) {
        snapshotDependencies = dependencies;
    }

    /**
     *
     */
    @Nullable public Revisions getRevisions() {
        return revisions;
    }

    /**
     * @param revisions Revisions.
     */
    public void setRevisions(List<Revision> revisions) {
        this.revisions = new Revisions();
        this.revisions.revisions(revisions);
    }

    /**
     * @param s Parameter key.
     */
    @Nullable public String parameter(String s) {
        return properties == null ? null : properties.getParameter(s);
    }

    /**
     * @return {@link #properties}
     */
    @Nullable public Parameters parameters() {
        return properties;
    }

    /**
     * @param parameters Parameters to be saved as {@link #properties}.
     */
    public void parameters(Parameters parameters) {
        this.properties = parameters;
    }
}
