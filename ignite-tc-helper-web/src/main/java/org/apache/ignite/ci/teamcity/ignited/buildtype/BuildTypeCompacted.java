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

package org.apache.ignite.ci.teamcity.ignited.buildtype;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.conf.bt.Parameters;
import org.apache.ignite.ci.tcmodel.conf.bt.SnapshotDependencies;
import org.apache.ignite.ci.tcmodel.conf.bt.SnapshotDependency;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.util.NumberUtil;
import org.jetbrains.annotations.Nullable;

@Persisted
public class BuildTypeCompacted extends BuildTypeRefCompacted implements IVersionedEntity {
    /** Build number counter. */
    private static String BUILD_NUMBER_COUNTER = "buildNumberCounter";

    /** Latest version. */
    private static final int LATEST_VERSION = 0;

    /** Entity fields version. */
    private short _ver = LATEST_VERSION;

    /** Build number counter. */
    private int buildNumberCounter;

    /** Settings. */
    @Nullable private ParametersCompacted settings;

    /** Properties. */
    @Nullable private ParametersCompacted parameters;

    /** Snapshot-dependencies. */
    @Nullable private List<SnapshotDependencyCompacted> snapshotDependencies;

    /**
     * @param compactor Compactor.
     * @param buildType BuildType.
     */
    public BuildTypeCompacted(IStringCompactor compactor, BuildTypeFull buildType) {
        super(compactor, buildType);

        buildNumberCounter = NumberUtil.parseInt(buildType.getSettings().getParameter(BUILD_NUMBER_COUNTER), 0);

        Parameters src = new Parameters(buildType.getSettings().properties());

        src.setParameter(BUILD_NUMBER_COUNTER, "");

        settings = new ParametersCompacted(compactor, src.properties());

        parameters = new ParametersCompacted(compactor, buildType.getParameters().properties());

        snapshotDependencies = new ArrayList<>();

        for (SnapshotDependency snDp : buildType.dependencies())
            snapshotDependencies.add(new SnapshotDependencyCompacted(compactor, snDp));
    }

    public int buildNumberCounter() {
        return buildNumberCounter;
    }

    public void buildNumberCounter(int buildNumberCounter) {
        this.buildNumberCounter = buildNumberCounter;
    }

    public BuildTypeFull toBuildType(IStringCompactor compactor) {
        BuildTypeFull res = new BuildTypeFull();

        fillBuildTypeRefFields(compactor, res);

        fillBuildTypeFields(compactor, res);

        return res;
    }

    protected void fillBuildTypeFields(IStringCompactor compactor, BuildTypeFull res) {
        res.setParameters(parameters == null ? new Parameters() : parameters.toParameters(compactor));
        res.setSettings(settings == null ? new Parameters() : settings.toParameters(compactor));
        res.setSetting(BUILD_NUMBER_COUNTER, Integer.toString(buildNumberCounter));

        List<SnapshotDependency> snDpList = null;

        if (snapshotDependencies != null) {
            snDpList = new ArrayList<>();

            for (SnapshotDependencyCompacted snDp : snapshotDependencies)
                snDpList.add(snDp.toSnapshotDependency(compactor));
        }

        res.setSnapshotDependencies(new SnapshotDependencies(snDpList));
    }

    protected void fillBuildTypeRefFields(IStringCompactor compactor, BuildType res) {
        String id = id(compactor);
        res.setId(id);
        res.setName(compactor.getStringFromId(super.name()));
        res.setProjectId(compactor.getStringFromId(super.projectId()));
        res.setProjectName(compactor.getStringFromId(super.projectName()));
        res.href = getHrefForId(id);
        res.setWebUrl(getWebUrlForId(id));
    }

    public ParametersCompacted settings() {
        return settings == null ? new ParametersCompacted() : settings;
    }

    public void settings(@Nullable ParametersCompacted settings) {
        this.settings = settings;
    }

    public ParametersCompacted parameters() {
        return parameters != null ? parameters : new ParametersCompacted();
    }

    public void parameters(@Nullable ParametersCompacted parameters) {
        this.parameters = parameters;
    }

    public List<SnapshotDependencyCompacted> snapshotDependencies() {
        return snapshotDependencies == null ? Collections.emptyList() : Collections.unmodifiableList(snapshotDependencies);
    }

    public void snapshotDependencies(
        @Nullable List<SnapshotDependencyCompacted> snapshotDependencies) {
        this.snapshotDependencies = snapshotDependencies;
    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildTypeCompacted))
            return false;

        if (!super.equals(o))
            return false;

        BuildTypeCompacted that = (BuildTypeCompacted)o;

        return _ver == that._ver &&
            buildNumberCounter == that.buildNumberCounter &&
            Objects.equals(settings(), that.settings()) &&
            Objects.equals(parameters(), that.parameters()) &&
            Objects.equals(snapshotDependencies(), that.snapshotDependencies());
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(super.hashCode(), _ver, buildNumberCounter, settings(), parameters(), snapshotDependencies());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("_ver", _ver)
            .add("buildNumberCounter", buildNumberCounter)
            .add("settings", settings)
            .add("parameters", parameters)
            .add("snapshotDependencies", snapshotDependencies)
            .toString();
    }
}
