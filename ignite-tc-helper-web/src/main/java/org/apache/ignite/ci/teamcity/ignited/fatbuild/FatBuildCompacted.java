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
package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@Persisted
public class FatBuildCompacted extends BuildRefCompacted implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 5;

    /** Default branch flag offset. */
    public static final int DEF_BR_F = 0;

    /** Composite flag offset. */
    public static final int COMPOSITE_F = 2;

    /**   flag offset. */
    public static final int FAKE_BUILD_F = 4;

    /** Failed to start flag offset. */
    public static final int FAILED_TO_START_F = 6;

    public static final int[] EMPTY = new int[0];

    /** Entity fields version. */
    private short _ver = LATEST_VERSION;

    /** Start date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long startDate;

    /** Finish date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long finishDate;

    /** Finish date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long queuedDate;

    /** Project ID, where suite is located. */
    private int projectId = -1;

    /** Suite Name for this builds. */
    private int name = -1;

    @Nullable private List<TestCompacted> tests;

    @Nullable private int snapshotDeps[];

    private BitSet flags = new BitSet();

    @Nullable private List<ProblemCompacted> problems;

    @Nullable private StatisticsCompacted statistics;

    @Nullable private int changesIds[];

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    /**
     * Default constructor.
     */
    public FatBuildCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param build Reference.
     */
    public FatBuildCompacted(IStringCompactor compactor, Build build) {
        super(compactor, build);

        startDate = build.getStartDate() == null ? -1L : build.getStartDate().getTime();
        finishDate = build.getFinishDate() == null ? -1L : build.getFinishDate().getTime();
        queuedDate = build.getQueuedDate() == null ? -1L : build.getQueuedDate().getTime();

        BuildType type = build.getBuildType();
        if (type != null) {
            projectId = compactor.getStringId(type.getProjectId());
            buildTypeName(type.getName(), compactor);
        }

        AtomicBoolean failedToStart = new AtomicBoolean();

        failedToStart.set(build.isFailedToStart());

        int[] arr = build.getSnapshotDependenciesNonNull()
                .stream()
                .peek(b -> {
                    if (failedToStart.get())
                        return;

                    if (b.hasUnknownStatus())
                        failedToStart.set(true);
                })
                .filter(b -> b.getId() != null)
                .mapToInt(BuildRef::getId)
                .toArray();

        snapshotDependencies(arr);

        setFlag(DEF_BR_F, build.defaultBranch);
        setFlag(COMPOSITE_F, build.composite);

        if (failedToStart.get())
            setFlag(FAILED_TO_START_F, true);

        if (build.isFakeStub())
            setFakeStub(true);
    }

    public void setFakeStub(boolean val) {
        setFlag(FAKE_BUILD_F, val);
    }

    public void buildTypeName(String btName, IStringCompactor compactor) {
        name = compactor.getStringId(btName);
    }

    public void snapshotDependencies(int[] arr) {
        snapshotDeps = arr.length > 0 ? arr : null;
    }

    /**
     * @param compactor Compacter.
     */
    public Build toBuild(IStringCompactor compactor) {
        Build res = new Build();

        fillBuildRefFields(compactor, res);

        fillBuildFields(compactor, res);

        return res;
    }

    /**
     * @param compactor Compactor.
     * @param res Response.
     */
    private void fillBuildFields(IStringCompactor compactor, Build res) {
        if (startDate > 0)
            res.setStartDateTs(startDate);

        if (finishDate > 0)
            res.setFinishDateTs(finishDate);

        if (queuedDate > 0)
            res.setQueuedDateTs(queuedDate);

        BuildType type = new BuildType();
        type.id(res.buildTypeId());
        type.name(buildTypeName(compactor));
        type.projectId(projectId(compactor));
        res.setBuildType(type);

        if (tests != null) {
            TestOccurrencesRef testOccurrencesRef = new TestOccurrencesRef();
            testOccurrencesRef.href = "/app/rest/latest/testOccurrences?locator=build:(id:" + id() + ")";
            testOccurrencesRef.count = tests.size();
            res.testOccurrences = testOccurrencesRef;
        }

        if (snapshotDeps != null) {
            List<BuildRef> snapshotDependencies = new ArrayList<>();
            for (int i = 0; i < snapshotDeps.length; i++) {
                int depId = snapshotDeps[i];

                BuildRef ref = new BuildRef();
                ref.setId(depId);
                ref.href = getHrefForId(depId);
                snapshotDependencies.add(ref);
            }

            res.snapshotDependencies(snapshotDependencies);
        }

        res.defaultBranch = getFlag(DEF_BR_F);
        res.composite = getFlag(COMPOSITE_F);

    }

    /**
     * @param compactor Compactor.
     * @param page Page.
     */
    public void addTests(IStringCompactor compactor, List<TestOccurrenceFull> page) {
        for (TestOccurrenceFull next : page) {
            TestCompacted compacted = new TestCompacted(compactor, next);

            if (tests == null)
                tests = new ArrayList<>();

            tests.add(compacted);
        }
    }

    /**
     * @param off Offset.
     * @param val Value.
     */
    private void setFlag(int off, Boolean val) {
        flags.clear(off, off + 2);

        boolean valPresent = val != null;
        flags.set(off, valPresent);

        if (valPresent)
            flags.set(off + 1, val);
    }


    /**
     * @param off Offset.
     */
    private Boolean getFlag(int off) {
        if (!flags.get(off))
            return null;

        return flags.get(off + 1);
    }

    /**
     * @param compactor Compactor.
     */
    public TestOccurrencesFull getTestOcurrences(IStringCompactor compactor) {
        if (tests == null)
            return new TestOccurrencesFull();

        List<TestOccurrenceFull> res = new ArrayList<>();

        for (TestCompacted compacted : tests)
            res.add(compacted.toTestOccurrence(compactor, id()));

        TestOccurrencesFull testOccurrences = new TestOccurrencesFull();

        testOccurrences.count = res.size();
        testOccurrences.setTests(res);

        return testOccurrences;
    }

    /** Start date. */
    @Nullable public Date getStartDate() {
        return startDate > 0 ? new Date(startDate) : null;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        FatBuildCompacted that = (FatBuildCompacted)o;
        return _ver == that._ver &&
            startDate == that.startDate &&
            finishDate == that.finishDate &&
            queuedDate == that.queuedDate &&
            projectId == that.projectId &&
            name == that.name &&
            Objects.equal(tests, that.tests) &&
            Objects.equal(snapshotDeps, that.snapshotDeps) &&
            Objects.equal(flags, that.flags) &&
                Objects.equal(problems, that.problems) &&
                Objects.equal(statistics, that.statistics)
                && Objects.equal(changesIds, that.changesIds);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(super.hashCode(), _ver, startDate, finishDate, queuedDate, projectId, name, tests,
                snapshotDeps, flags, problems, statistics, changesIds);
    }

    /**
     *
     */
    public boolean isComposite() {
        Boolean flag = getFlag(COMPOSITE_F);

        return flag != null && flag;
    }

    /**
     *
     */
    public boolean isFakeStub() {
        if (getId() == null)
            return true;

        Boolean flag = getFlag(FAKE_BUILD_F);

        return flag != null && flag;
    }

    /**
     *
     */
    public boolean isFailedToStart() {
        Boolean flag = getFlag(FAILED_TO_START_F);

        return flag != null && flag;
    }

    public Stream<TestCompacted> getFailedNotMutedTests(IStringCompactor compactor) {
        if (tests == null)
            return Stream.of();

        return tests.stream()
                .filter(t -> t.isFailedButNotMuted(compactor));
    }

    public Stream<String> getFailedNotMutedTestNames(IStringCompactor compactor) {
        return getFailedNotMutedTests(compactor).map(t -> t.testName(compactor));
    }

    public Stream<TestCompacted> getAllTests() {
        if (tests == null)
            return Stream.of();

        return tests.stream();
    }

    public int getTestsCount() {
        return tests != null ? tests.size() : 0;
    }

    public Stream<String> getAllTestNames(IStringCompactor compactor) {
        return getAllTests().map(t -> t.testName(compactor));
    }

    public String buildTypeName(IStringCompactor compactor) {
        return compactor.getStringFromId(name);
    }

    public String projectId(IStringCompactor compactor) {
        return compactor.getStringFromId(projectId);
    }

    public List<ProblemOccurrence> problems(IStringCompactor compactor) {
        if (this.problems == null)
             return Collections.emptyList();

        return this.problems.stream()
                .map(pc -> pc.toProblemOccurrence(compactor, id()))
                .collect(Collectors.toList());
    }

    public List<ProblemCompacted> problems() {
        if (this.problems == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(this.problems);
    }

    public void addProblems(IStringCompactor compactor,
                            @NotNull List<ProblemOccurrence> occurrences) {
        if (occurrences.isEmpty())
            return;

        if (this.problems == null)
            this.problems = new ArrayList<>();

        occurrences.stream()
                .map(p -> new ProblemCompacted(compactor, p))
                .forEach(this.problems::add);
    }

    public Long buildDuration(IStringCompactor compactor) {
        return statistics == null ? null : statistics.buildDuration(compactor);
    }

    public void statistics(IStringCompactor compactor, Statistics statistics) {
        this.statistics = new StatisticsCompacted(compactor, statistics);
    }

    public void changes(int[] changes) {
        this.changesIds = changes;
    }

    public int[] changes() {
        if (changesIds == null)
            return EMPTY;

        return changesIds;
    }

    public int[] snapshotDependencies() {
        if (snapshotDeps == null)
            return EMPTY;

        return snapshotDeps.clone();
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("_", super.toString())
            .add("_ver", _ver)
            .add("startDate", startDate)
            .add("finishDate", finishDate)
            .add("queuedDate", queuedDate)
            .add("projectId", projectId)
            .add("name", name)
            .add("tests", tests)
            .add("snapshotDeps", snapshotDeps)
            .add("flags", flags)
            .add("problems", problems)
            .add("statistics", statistics)
            .add("changesIds", changesIds)
            .toString();
    }
}
