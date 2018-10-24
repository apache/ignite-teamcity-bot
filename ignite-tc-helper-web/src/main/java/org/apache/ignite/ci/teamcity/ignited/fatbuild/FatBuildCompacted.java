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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
@Persisted
public class FatBuildCompacted extends BuildRefCompacted implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 0;

    /** Entity fields version. */
    private short _ver;

    /** Start date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long startDate;

    /** Finish date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long finishDate;

    /** Finish date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long queuedDate;

    private int projectId = -1;
    private int name = -1;

    @Nullable private List<TestCompacted> tests;

    @Nullable private int snapshotDeps[];

    private BitSet flags = new BitSet();

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
            name = compactor.getStringId(type.getName());
        }

        int[] arr = build.getSnapshotDependenciesNonNull().stream()
            .filter(b -> b.getId() != null).mapToInt(BuildRef::getId).toArray();

        snapshotDeps = arr.length > 0 ? arr : null;

        setFlag(0, build.defaultBranch);
        setFlag(2, build.composite);
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
        type.name(compactor.getStringFromId(name));
        type.projectId(compactor.getStringFromId(projectId));
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

        res.defaultBranch = getFlag(0);
        res.composite = getFlag(2);
    }

    /**
     * @param compactor Compactor.
     * @param page Page.
     */
    public void addTests(IStringCompactor compactor, List<TestOccurrence> page) {
        for (TestOccurrence next : page) {
            TestCompacted compacted = new TestCompacted(compactor, next);

            if (tests == null)
                tests = new ArrayList<>();

            tests.add(compacted);
        }
    }


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
    public TestOccurrences getTestOcurrences(IStringCompactor compactor) {
        if (tests == null)
            return new TestOccurrences();

        List<TestOccurrence> res = new ArrayList<>();

        for (TestCompacted compacted : tests)
            res.add(compacted.toTestOccurrence(compactor, id()));

        TestOccurrences testOccurrences = new TestOccurrences();

        testOccurrences.setTests(res);
        testOccurrences.count = res.size();

        return testOccurrences;
    }
}
