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

package org.apache.ignite.tcbot.engine.build;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.buildlog.ITestLogCheckResult;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;

import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;

/**
 * Builds an AI prompt with TeamCity failure context.
 */
public class TestFailuresAiPromptBuilder {
    /** Default limit for a single TeamCity failure details block. */
    public static final int DFLT_MAX_DETAILS_CHARS = 20000;

    /** String compactor. */
    private final IStringCompactor compactor;

    /**
     * @param compactor String compactor.
     */
    public TestFailuresAiPromptBuilder(IStringCompactor compactor) {
        this.compactor = compactor;
    }

    /**
     * @param tcIgnited TeamCity facade.
     * @param ctx Full chain context.
     * @param baseBranchTc Base branch for failure-rate history.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means no limit.
     */
    @Nonnull public String buildPrompt(ITeamcityIgnited tcIgnited, FullChainRunCtx ctx,
        @Nullable String baseBranchTc, int maxDetailsChars) {
        return buildPrompt(tcIgnited, ctx, baseBranchTc, maxDetailsChars, null);
    }

    /**
     * @param tcIgnited TeamCity facade.
     * @param ctx Full chain context.
     * @param baseBranchTc Base branch for failure-rate history.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means no limit.
     * @param testNameFilter Optional full test name filter.
     */
    @Nonnull public String buildPrompt(ITeamcityIgnited tcIgnited, FullChainRunCtx ctx,
        @Nullable String baseBranchTc, int maxDetailsChars, @Nullable String testNameFilter) {
        StringBuilder res = new StringBuilder();

        String normalizedBaseBranch = normalizeBranch(baseBranchTc);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        appendHeader(res, tcIgnited, ctx, normalizedBaseBranch);

        AtomicInteger suiteCnt = new AtomicInteger();
        AtomicInteger testCnt = new AtomicInteger();

        List<MultBuildRunCtx> failedSuites = ctx.suites()
            .filter(suite -> !suite.isComposite())
            .filter(MultBuildRunCtx::isFailed)
            .filter(suite -> testNameFilter == null
                || suite.getFailedTests().stream().anyMatch(test -> testNameFilter.equals(test.getName())))
            .collect(Collectors.toList());

        if (failedSuites.isEmpty())
            res.append("No failed suites were found in the TeamCity context.\n");

        failedSuites.forEach(suite -> {
            suiteCnt.incrementAndGet();
            appendSuite(res, tcIgnited, suite, baseBranchId);

            List<TestCompactedMult> failedTests = suite.getFailedTests()
                .stream()
                .filter(test -> testNameFilter == null || testNameFilter.equals(test.getName()))
                .collect(Collectors.toList());

            if (failedTests.isEmpty())
                res.append("No failed non-muted tests were reported for this suite.\n\n");
            else {
                for (TestCompactedMult test : failedTests) {
                    testCnt.incrementAndGet();
                    appendTest(res, tcIgnited, suite, test, baseBranchId, maxDetailsChars);
                }
            }
        });

        res.append("## Summary\n");
        res.append("Failed suites included: ").append(suiteCnt.get()).append('\n');
        res.append("Failed tests included: ").append(testCnt.get()).append('\n');

        return res.toString();
    }

    /**
     * @param res Result builder.
     * @param tcIgnited TeamCity facade.
     * @param ctx Full chain context.
     * @param normalizedBaseBranch Normalized base branch.
     */
    private void appendHeader(StringBuilder res, ITeamcityIgnited tcIgnited, FullChainRunCtx ctx,
        @Nullable String normalizedBaseBranch) {
        res.append("# AI Prompt: Investigate TeamCity Test Failures\n\n");
        res.append("You are working in the local checkout of the project that produced these TeamCity failures. ");
        res.append("Use the TeamCity context below to identify likely root causes and propose a minimal fix. ");
        res.append("Prefer concrete code locations, explain uncertainty, and mention whether the failure ");
        res.append("looks flaky, ");
        res.append("environmental, or caused by the current change.\n\n");

        res.append("## Build Context\n");
        appendLine(res, "TeamCity server", tcIgnited.serverCode());
        appendLine(res, "TeamCity host", tcIgnited.host());
        appendLine(res, "Chain", ctx.suiteName());
        appendLine(res, "Suite id", ctx.suiteId());
        appendLine(res, "Branch", ctx.branchName());
        appendLine(res, "Base branch for history", normalizedBaseBranch);
        appendLine(res, "Entry build id", String.valueOf(ctx.getSuiteBuildId()));
        appendLine(res, "Entry build URL", tcIgnited.host() + "viewLog.html?buildId=" + ctx.getSuiteBuildId());
        appendLine(res, "Duration", ctx.getDurationPrintable(suite -> true));
        appendLine(res, "Tests duration", ctx.getTestsDurationPrintable(suite -> true));
        res.append('\n');
    }

    /**
     * @param res Result builder.
     * @param tcIgnited TeamCity facade.
     * @param suite Suite context.
     * @param baseBranchId Base branch compacted id.
     */
    private void appendSuite(StringBuilder res, ITeamcityIgnited tcIgnited, MultBuildRunCtx suite,
        @Nullable Integer baseBranchId) {
        IRunHistory baseHist = suite.history(tcIgnited, baseBranchId, null);

        res.append("## Suite: ").append(nullToUnknown(suite.suiteName())).append('\n');
        appendLine(res, "Suite id", suite.suiteId());
        appendLine(res, "Project id", suite.projectId());
        appendLine(res, "Build id", String.valueOf(suite.getBuildId()));
        appendLine(res, "Build URL", tcIgnited.host() + "viewLog.html?buildId=" + suite.getBuildId());
        appendLine(res, "Branch", suite.branchName());
        appendLine(res, "Result", suite.getResult());
        appendLine(res, "Failed tests", String.valueOf(suite.failedTests()));
        appendLine(res, "Total tests", String.valueOf(suite.totalTests()));
        appendLine(res, "Duration", millisToDurationPrintable(suite.buildDuration()));
        appendLine(res, "Net duration", millisToDurationPrintable(suite.buildDurationNetTime()));
        appendLine(res, "Tests duration", millisToDurationPrintable(suite.getAvgTestsDuration()));
        appendLine(res, "Last change users", suite.lastChangeUsers().collect(Collectors.joining(", ")));

        if (baseHist != null) {
            appendLine(res, "Base branch suite failure rate", baseHist.getFailPercentPrintable() + "%");
            appendLine(res, "Base branch suite critical failure rate",
                baseHist.getCriticalFailPercentPrintable() + "%");
        }

        String blockerComment = suite.getPossibleBlockerComment(compactor, baseHist, tcIgnited.config());
        appendLine(res, "Possible blocker note", blockerComment);

        appendProblems(res, suite);
        appendLogChecks(res, suite);

        res.append('\n');
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     */
    private void appendProblems(StringBuilder res, MultBuildRunCtx suite) {
        List<ProblemOccurrence> problems = suite.allProblemsInAllBuilds()
            .map(problem -> problem.toProblemOccurrence(compactor, suite.getBuildId()))
            .collect(Collectors.toList());

        if (problems.isEmpty())
            return;

        res.append("TeamCity build problems:\n");

        for (ProblemOccurrence problem : problems) {
            res.append("- type=").append(nullToUnknown(problem.type));
            res.append(", identity=").append(nullToUnknown(problem.identity));

            if (problem.buildRef != null && problem.buildRef.getId() != null)
                res.append(", actualBuildId=").append(problem.buildRef.getId());

            res.append('\n');
        }
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     */
    private void appendLogChecks(StringBuilder res, MultBuildRunCtx suite) {
        List<Map.Entry<String, ITestLogCheckResult>> warnings = suite.getLogsCheckResults()
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> entry.getValue() != null && entry.getValue().hasWarns())
            .collect(Collectors.toList());

        if (warnings.isEmpty())
            return;

        res.append("Log scanner warnings:\n");

        for (Map.Entry<String, ITestLogCheckResult> entry : warnings) {
            ITestLogCheckResult checkRes = entry.getValue();

            res.append("- ").append(nullToUnknown(entry.getKey()));
            res.append(" (log size ").append(checkRes.getLogSizeBytes()).append(" bytes)\n");

            for (String warn : checkRes.getWarns())
                res.append("  ").append(warn).append('\n');
        }
    }

    /**
     * @param res Result builder.
     * @param tcIgnited TeamCity facade.
     * @param suite Suite context.
     * @param test Test context.
     * @param baseBranchId Base branch compacted id.
     * @param maxDetailsChars Max details chars.
     */
    private void appendTest(StringBuilder res, ITeamcityIgnited tcIgnited, MultBuildRunCtx suite,
        TestCompactedMult test, @Nullable Integer baseBranchId, int maxDetailsChars) {
        String fullName = test.getName();
        IRunHistory testHist = test.history(tcIgnited, baseBranchId);

        res.append("### Failed Test: ").append(nullToUnknown(fullName)).append('\n');
        appendLine(res, "Short suite", ShortTestFailureUi.extractSuite(Strings.nullToEmpty(fullName).split("\\:")[0]));
        appendLine(res, "Short test", extractShortTest(fullName));
        appendLine(res, "Failures in loaded context", String.valueOf(test.failuresCount()));
        appendLine(res, "Average duration", millisToDurationPrintable(test.getAvgDurationMs()));
        appendLine(res, "Investigated in TeamCity", String.valueOf(test.isInvestigated()));
        appendLine(res, "Possible blocker note", test.getPossibleBlockerComment(testHist));

        if (testHist != null) {
            appendLine(res, "Base branch test failure rate", testHist.getFailPercentPrintable() + "%");
            appendLine(res, "Base branch flaky", String.valueOf(testHist.isFlaky()));
            appendLine(res, "Base branch latest runs", String.valueOf(testHist.getLatestRunResults()));
        }

        AtomicInteger invocationIdx = new AtomicInteger();

        test.getInvocationsStream()
            .filter(Objects::nonNull)
            .filter(invocation -> invocation.isFailedButNotMuted(compactor))
            .forEach(invocation -> appendInvocation(res, tcIgnited, suite, invocation, invocationIdx.incrementAndGet(),
                maxDetailsChars));

        res.append('\n');
    }

    /**
     * @param res Result builder.
     * @param tcIgnited TeamCity facade.
     * @param suite Suite context.
     * @param invocation Single test invocation.
     * @param idx Invocation index.
     * @param maxDetailsChars Max details chars.
     */
    private void appendInvocation(StringBuilder res, ITeamcityIgnited tcIgnited, MultBuildRunCtx suite,
        ITest invocation, int idx, int maxDetailsChars) {
        int actualBuildId = invocation.getActualBuildId() > 0 ? invocation.getActualBuildId() : suite.getBuildId();

        res.append("Invocation ").append(idx).append(":\n");
        appendLine(res, "Status", compactor.getStringFromId(invocation.status()));
        Integer duration = invocation.getDuration();

        appendLine(res, "Duration", millisToDurationPrintable(duration == null ? null : duration.longValue()));
        appendLine(res, "Actual build id", String.valueOf(actualBuildId));
        appendLine(res, "Build URL", tcIgnited.host() + "viewLog.html?buildId=" + actualBuildId);
        appendLine(res, "TeamCity test id", String.valueOf(invocation.getTestId()));
        appendLine(res, "Muted", String.valueOf(invocation.isMutedTest()));
        appendLine(res, "Ignored", String.valueOf(invocation.isIgnoredTest()));
        appendLine(res, "Currently muted", String.valueOf(invocation.getCurrentlyMuted()));
        appendLine(res, "Currently investigated", String.valueOf(invocation.getCurrInvestigatedFlag()));

        String details = invocation.getDetailsText();

        if (Strings.isNullOrEmpty(details))
            res.append("TeamCity failure details: <empty>\n");
        else {
            res.append("TeamCity failure details:\n");
            res.append("```text\n");
            res.append(limit(details.trim(), maxDetailsChars));
            res.append("\n```\n");
        }
    }

    /**
     * @param fullName Full test name.
     */
    @Nullable private String extractShortTest(@Nullable String fullName) {
        if (fullName == null)
            return null;

        String[] split = fullName.split("\\:");

        if (split.length < 2)
            return null;

        return ShortTestFailureUi.extractTest(split[1]);
    }

    /**
     * @param res Result builder.
     * @param name Field name.
     * @param val Field value.
     */
    private void appendLine(StringBuilder res, String name, @Nullable String val) {
        if (Strings.isNullOrEmpty(val))
            return;

        res.append("- ").append(name).append(": ").append(val).append('\n');
    }

    /**
     * @param val Value.
     */
    private String nullToUnknown(@Nullable String val) {
        return Strings.isNullOrEmpty(val) ? "<unknown>" : val;
    }

    /**
     * @param text Text.
     * @param maxChars Limit.
     */
    private String limit(String text, int maxChars) {
        if (maxChars <= 0 || text.length() <= maxChars)
            return text;

        return text.substring(0, maxChars)
            + "\n\n... truncated " + (text.length() - maxChars) + " chars. "
            + "Increase maxDetailsChars or use 0 for unlimited details.";
    }
}
