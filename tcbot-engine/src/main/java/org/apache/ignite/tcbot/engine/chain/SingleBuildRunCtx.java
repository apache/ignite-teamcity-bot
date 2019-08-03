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

package org.apache.ignite.tcbot.engine.chain;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.tcbot.common.conf.IBuildParameterSpec;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.buildlog.ILogCheckResult;
import org.apache.ignite.tcignited.buildlog.ITestLogCheckResult;
import org.apache.ignite.tcservice.ITeamcity;

/**
 * Single build occurrence with changes, tag and log results.
 */
public class SingleBuildRunCtx implements ISuiteResults {
    /** Build compacted. */
    private FatBuildCompacted buildCompacted;

    /** Compactor. */
    private IStringCompactor compactor;

    /** Changes. */
    private List<ChangeCompacted> changes = new ArrayList<>();

    /** Logger check result future. */
    private CompletableFuture<ILogCheckResult> logCheckResFut;

    /** Tags found from filtering-enabled parameters. */
    private Set<String> tags = new HashSet<>();

    /**
     * @param buildCompacted Build compacted.
     * @param compactor Compactor.
     */
    public SingleBuildRunCtx(FatBuildCompacted buildCompacted,
        IStringCompactor compactor) {
        this.buildCompacted = buildCompacted;
        this.compactor = compactor;
    }

    /**
     *
     */
    public Integer buildId() {
        Preconditions.checkNotNull(buildCompacted);
        return buildCompacted.id() < 0 ? null : buildCompacted.id();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMetricProblem() {
        return getProblemsStream().anyMatch(p -> p.isBuildFailureOnMetric(compactor));
    }

    /** {@inheritDoc} */
    @Override public boolean hasCompilationProblem() {
        return getProblemsStream().anyMatch(p -> p.isCompilationError(compactor));
    }


    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(p -> p.isExecutionTimeout(compactor)).count();
    }

    Stream<ProblemCompacted> getProblemsStream() {
        return buildCompacted.problems().stream();
    }

    @Override public boolean hasJvmCrashProblem() {
        return getProblemsStream().anyMatch(p -> p.isJvmCrash(compactor));
    }

    @Override public boolean hasOomeProblem() {
        return getProblemsStream().anyMatch(p -> p.isOome(compactor));
    }

    @Override public boolean hasExitCodeProblem() {
        return getProblemsStream().anyMatch(p -> p.isExitCode(compactor));
    }

    @Override public String suiteId() {
        return compactor.getStringFromId(buildCompacted.buildTypeId());
    }

    @Override public boolean hasBuildMessageProblem() {
        return getProblemsStream().anyMatch(p -> p.isBuildFailureOnMessage(compactor));
    }

    public void setLogCheckResFut(CompletableFuture<ILogCheckResult> logCheckResFut) {
        this.logCheckResFut = logCheckResFut;
    }

    @Nullable public String getCriticalFailLastStartedTest() {
        ILogCheckResult logCheckRes = getLogCheckIfFinished();
        if (logCheckRes == null)
            return null;

        return logCheckRes.getLastStartedTest(compactor);
    }

    @Nullable public Map<String, ITestLogCheckResult> getTestLogCheckResult() {
        ILogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null)
            return null;

        return logCheckRes.getTestLogCheckResult();
    }

    @Nullable public Integer getBuildIdIfHasThreadDump() {
        ILogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null || !logCheckRes.hasThreadDump())
            return null;

        return !Strings.isNullOrEmpty(logCheckRes.getLastThreadDump()) ? buildId() : null;
    }

    @Nullable public ILogCheckResult getLogCheckIfFinished() {
        if (logCheckResFut == null)
            return null;

        if (!logCheckResFut.isDone() || logCheckResFut.isCancelled())
            return null;

        ILogCheckResult logCheckRes = FutureUtil.getResultSilent(logCheckResFut);

        if (logCheckRes == null)
            return null;

        return logCheckRes;
    }

    public List<ChangeCompacted> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    @Nonnull Stream<? extends Future<?>> getFutures() {
        return logCheckResFut == null ? Stream.empty() : Stream.of((Future<?>)logCheckResFut);
    }

    public boolean isComposite() {
        return buildCompacted.isComposite();
    }

    public String getBranch() {
        return compactor.getStringFromId(buildCompacted.branchName());
    }

    /**
     * @return Names of not muted or ignored test failed for non composite build
     */
    public Stream<String> getFailedNotMutedTestNames() {
        return isComposite() ? Stream.empty() : buildCompacted.getFailedNotMutedTestNames(compactor);
    }

    public Stream<String> getAllTestNames() {
        return buildCompacted.getAllTestNames(compactor);
    }

    public Stream<ITest> getAllTests() {
        return isComposite() ? Stream.empty() : buildCompacted.getAllTests();
    }

    public String suiteName() {
        return buildCompacted.buildTypeName(compactor);
    }

    public String projectId() {
        return buildCompacted.projectId(compactor);
    }

    @Nullable public Long buildDuration() {
        return buildCompacted.buildDuration(compactor);
    }

    @Nullable public Long buildDurationNetTime() {
        return buildCompacted.buildDurationNetTime(compactor);
    }

    @Nullable public Long sourceUpdateDuration() {
        return buildCompacted.sourceUpdateDuration(compactor);
    }

    @Nullable public Long artifcactPublishingDuration() {
        return buildCompacted.artifcactPublishingDuration(compactor);
    }

    @Nullable public Long dependeciesResolvingDuration() {
        return buildCompacted.dependeciesResolvingDuration(compactor);
    }

    public void setChanges(Collection<ChangeCompacted> changes) {
        this.changes.clear();
        this.changes.addAll(changes);
    }

    public boolean isCancelled() {
        return buildCompacted.isCancelled(compactor);
    }

    /**
     * @return Full run time required to run tests.
     */
    public long testsDuration() {
        return getAllTests()
            .mapToLong(t -> {
                Integer duration = t.getDuration();
                if (duration == null)
                    return 0;

                return duration;
            }).sum();
    }

    public void addTag(@Nullable String lb) {
        if(Strings.isNullOrEmpty(lb))
            return;

        this.tags.add(lb);
    }

    /**
     * @return Build tags associated with this run. May be Java version, area of responibilty - any text mark.
     */
    public Set<String> tags() {
        return tags;
    }

    public void addTagsFromParameters(ParametersCompacted parameters, ITcServerConfig tcCfg,
        IStringCompactor compactor) {
        for (IBuildParameterSpec parm0 : tcCfg.filteringParameters()) {
            if (!parm0.isFilled())
                continue;

            String propVal = getPropertyOrSpecialValue(parameters, compactor, parm0.name());

            if (Strings.isNullOrEmpty(propVal))
                continue;

            parm0.selection().stream()
                .filter(pvs -> {
                    String valRegExp = pvs.valueRegExp();

                    if(!Strings.isNullOrEmpty(valRegExp))
                        return Pattern.compile(valRegExp).matcher(propVal).find();

                    String exactVal = pvs.value();

                    if(!Strings.isNullOrEmpty(exactVal))
                        return Objects.equals(exactVal, propVal);

                    return false;
                })
                .findAny()
                .ifPresent(v -> addTag(v.label()));

        }
    }

    /**
     * @param parameters Parameters from build.
     * @param compactor Compactor.
     * @param parmKey Parmeters key.
     */
    public String getPropertyOrSpecialValue(ParametersCompacted parameters, IStringCompactor compactor,
        String parmKey) {

        String propVal;
        if (ITeamcity.SUITE_ID_PROPERTY.equals(parmKey))
            propVal = suiteId();
        else if (ITeamcity.SUITE_NAME_PROPERTY.equals(parmKey))
            propVal = suiteName();
        else
            propVal = parameters.getProperty(compactor, parmKey);

        return propVal;
    }

    public boolean hasBuildProblemType(int id) {
        return buildCompacted.hasBuildProblemType(id);
    }

    public boolean hasSuiteIncompleteFailure() {
        return hasJvmCrashProblem()
            || hasTimeoutProblem()
            || hasOomeProblem()
            || hasExitCodeProblem()
            || hasCompilationProblem()
            || hasMetricProblem();
    }

    public int totalNotMutedTests() {
        return buildCompacted.totalNotMutedTests();
    }

    public int buildTypeIdId() {
        return buildCompacted.buildTypeId();
    }
}
