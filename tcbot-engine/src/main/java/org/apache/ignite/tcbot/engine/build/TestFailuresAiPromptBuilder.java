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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.SingleBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.buildlog.ITestLogCheckResult;
import org.apache.ignite.tcignited.history.InvocationData;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;

import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;

/**
 * Builds an AI prompt with TeamCity failure context.
 */
public class TestFailuresAiPromptBuilder {
    /** Default limit for a single TeamCity failure details block. */
    public static final int DFLT_MAX_DETAILS_CHARS = 40000;

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

        res.append("## Included Scope\n");
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
        res.append("# AI Prompt: Investigate TeamCity Failure\n\n");
        res.append("You are in the local checkout of the project that produced this TeamCity failure. ");
        res.append("Use the failure text first, then the log snippets and history, to find a likely root cause. ");
        res.append("Propose a minimal fix, name concrete files/classes to inspect, and say whether this looks flaky, ");
        res.append("environmental, or caused by the current change.\n\n");

        res.append("## Run Context\n");
        appendLine(res, "TeamCity server", tcIgnited.serverCode());
        appendLine(res, "Chain", ctx.suiteName());
        appendLine(res, "Suite id", ctx.suiteId());
        appendLine(res, "Branch", ctx.branchName());
        appendLine(res, "Base branch for history", normalizedBaseBranch);
        appendLine(res, "Entry build id", String.valueOf(ctx.getSuiteBuildId()));
        appendLine(res, "Entry build", tcIgnited.host() + "viewLog.html?buildId=" + ctx.getSuiteBuildId());
        appendLine(res, "Duration", ctx.getDurationPrintable(suite -> true));
        res.append('\n');

        res.append("## Investigation Hints\n");
        res.append("- Start from the first exception/assertion and the nearest project frame in the failure details.\n");
        res.append("- Search the checkout for the failing class, method, assertion text, and error message.\n");
        res.append("- Use the log snippets for nearby Ignite errors, timeouts, deadlocks, OOM/crash markers, and the last started test.\n");
        res.append("- Use history only to decide whether this is new, flaky, or likely environmental.\n\n");
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

        res.append("## Suite Context: ").append(nullToUnknown(suite.suiteName())).append('\n');
        appendLine(res, "Suite id", suite.suiteId());
        appendLine(res, "Build id", String.valueOf(suite.getBuildId()));
        appendLine(res, "Build", tcIgnited.host() + "viewLog.html?buildId=" + suite.getBuildId());
        appendLine(res, "Branch", suite.branchName());
        appendLine(res, "Result", suite.getResult());
        appendLine(res, "Failed tests", String.valueOf(suite.failedTests()));
        appendLine(res, "Duration", millisToDurationPrintable(suite.buildDuration()));
        appendLine(res, "Execution parameters", executionParameters(suite));
        appendLine(res, "Last change users", compactCsv(suite.lastChangeUsers().collect(Collectors.toList())));

        if (baseHist != null) {
            appendLine(res, "Base branch suite failure rate", baseHist.getFailPercentPrintable() + "%");
            appendLine(res, "Base branch suite critical failure rate",
                baseHist.getCriticalFailPercentPrintable() + "%");
        }

        String blockerComment = suite.getPossibleBlockerComment(compactor, baseHist, tcIgnited.config());
        appendLine(res, "Possible blocker note", blockerComment);

        appendProblems(res, suite);

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

        res.append("Build log scanner warnings:\n");

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

        res.append("## Failed Test: ").append(nullToUnknown(fullName)).append('\n');
        appendLine(res, "Short suite", ShortTestFailureUi.extractSuite(Strings.nullToEmpty(fullName).split("\\:")[0]));
        appendLine(res, "Short test", extractShortTest(fullName));
        appendLine(res, "Failures in loaded context", String.valueOf(test.failuresCount()));
        appendLine(res, "Investigated in TeamCity", String.valueOf(test.isInvestigated()));
        appendLine(res, "Possible blocker note", test.getPossibleBlockerComment(testHist));

        if (testHist != null) {
            appendLine(res, "Base branch test failure rate", testHist.getFailPercentPrintable() + "%");
            appendLine(res, "Base branch flaky", String.valueOf(testHist.isFlaky()));
            appendLine(res, "Base branch latest runs", String.valueOf(testHist.getLatestRunResults()));
            appendLine(res, "Break boundary", breakBoundary(testHist));
            appendLine(res, "Recent execution history", recentHistory(testHist, 12));
        }

        appendRelevantLogChecks(res, suite, fullName);

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
        appendLine(res, "TeamCity test REST", testOccurrenceRestUrl(tcIgnited, invocation, actualBuildId));
        appendLine(res, "Muted", String.valueOf(invocation.isMutedTest()));
        appendLine(res, "Ignored", String.valueOf(invocation.isIgnoredTest()));
        appendLine(res, "Currently muted", String.valueOf(invocation.getCurrentlyMuted()));
        appendLine(res, "Currently investigated", String.valueOf(invocation.getCurrInvestigatedFlag()));

        String details = invocation.getDetailsText();

        if (Strings.isNullOrEmpty(details))
            res.append("TeamCity failure details: <empty>\n");
        else {
            res.append("Failure details text from TeamCity:\n");
            res.append("```text\n");
            res.append(limit(cleanDetails(details), maxDetailsChars));
            res.append("\n```\n");
        }
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     * @param fullName Full test name.
     */
    private void appendRelevantLogChecks(StringBuilder res, MultBuildRunCtx suite, @Nullable String fullName) {
        List<String> snippets = new ArrayList<>();

        suite.getLogsCheckResults()
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> isSameTestLog(entry.getKey(), fullName))
            .filter(entry -> entry.getValue() != null)
            .forEach(entry -> {
                ITestLogCheckResult checkRes = entry.getValue();

                if (!checkRes.hasWarns())
                    return;

                snippets.add("Log grep for " + entry.getKey() + " (log size "
                    + checkRes.getLogSizeBytes() + " bytes):");

                snippets.addAll(checkRes.getWarns());
            });

        if (snippets.isEmpty()) {
            res.append("Log grep from processed build log: <no warning snippets found for this test>\n");

            return;
        }

        res.append("Log grep from processed build log:\n");
        res.append("```text\n");

        for (String snippet : snippets)
            res.append(snippet).append('\n');

        res.append("```\n");
    }

    /**
     * @param logTestName Test name from log scanner.
     * @param fullName Full TeamCity test name.
     */
    private boolean isSameTestLog(@Nullable String logTestName, @Nullable String fullName) {
        if (Strings.isNullOrEmpty(logTestName) || Strings.isNullOrEmpty(fullName))
            return false;

        if (Objects.equals(logTestName, fullName))
            return true;

        String shortTest = extractShortTest(fullName);

        return !Strings.isNullOrEmpty(shortTest) && logTestName.contains(shortTest);
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
     * @param tcIgnited TeamCity facade.
     * @param invocation Invocation.
     * @param actualBuildId Actual build id.
     */
    @Nullable private String testOccurrenceRestUrl(ITeamcityIgnited tcIgnited, ITest invocation, int actualBuildId) {
        if (invocation.idInBuild() < 0 || actualBuildId <= 0)
            return null;

        return tcIgnited.host() + "app/rest/latest/testOccurrences/id:" + invocation.idInBuild()
            + ",build:(id:" + actualBuildId + ")";
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

    /**
     * @param details Details from TeamCity.
     */
    private String cleanDetails(String details) {
        return details.trim()
            .replace(" ------- Stdout: ------- ", "\n\n------- Stdout: -------\n")
            .replace(" ------- Stderr: ------- ", "\n\n------- Stderr: -------\n");
    }

    /**
     * @param vals Values.
     */
    private String compactCsv(List<String> vals) {
        if (vals == null || vals.isEmpty())
            return null;

        return vals.stream()
            .filter(v -> !Strings.isNullOrEmpty(v))
            .distinct()
            .limit(10)
            .collect(Collectors.joining(", "));
    }

    /**
     * @param suite Suite context.
     */
    @Nullable private String executionParameters(MultBuildRunCtx suite) {
        List<String> params = new ArrayList<>();

        suite.buildsStream()
            .map(SingleBuildRunCtx::buildParameters)
            .filter(Objects::nonNull)
            .forEach(buildParams -> collectInterestingParameters(buildParams, params));

        if (params.isEmpty())
            return null;

        return params.stream().distinct().limit(20).collect(Collectors.joining("; "));
    }

    /**
     * @param buildParams Build parameters.
     * @param params Destination.
     */
    private void collectInterestingParameters(ParametersCompacted buildParams, List<String> params) {
        buildParams.forEach(compactor, (key, val) -> {
            String lower = key == null ? "" : key.toLowerCase();

            if (lower.contains("os")
                || lower.contains("agent")
                || lower.contains("java")
                || lower.contains("jdk")
                || lower.contains("jvm")
                || lower.contains("runner")
                || lower.contains("env."))
                params.add(key + "=" + val);
        });
    }

    /**
     * @param hist Run history.
     */
    @Nullable private String breakBoundary(IRunHistory hist) {
        List<Invocation> invocations = hist.getInvocations()
            .filter(inv -> inv.status() != InvocationData.MISSING)
            .filter(inv -> !Invocation.isMutedOrIgnored(inv.status()))
            .sorted(Comparator.comparing(Invocation::buildId))
            .collect(Collectors.toList());

        Invocation lastOk = null;
        Invocation firstBadAfterOk = null;

        for (Invocation inv : invocations) {
            if (inv.status() == InvocationData.OK) {
                lastOk = inv;
                firstBadAfterOk = null;
            }
            else if (isFailure(inv) && lastOk != null && firstBadAfterOk == null)
                firstBadAfterOk = inv;
        }

        if (lastOk == null || firstBadAfterOk == null)
            return null;

        return "last OK build " + lastOk.buildId()
            + ", first later failure build " + firstBadAfterOk.buildId()
            + ", change state at first failure " + firstBadAfterOk.changesState();
    }

    /**
     * @param hist Run history.
     * @param limit Limit.
     */
    @Nullable private String recentHistory(IRunHistory hist, int limit) {
        List<Invocation> recent = hist.getInvocations()
            .filter(inv -> inv.status() != InvocationData.MISSING)
            .sorted(Comparator.comparing(Invocation::buildId).reversed())
            .limit(limit)
            .sorted(Comparator.comparing(Invocation::buildId))
            .collect(Collectors.toList());

        if (recent.isEmpty())
            return null;

        return recent.stream()
            .map(inv -> inv.buildId() + ":" + statusName(inv.status()) + ":" + inv.changesState())
            .collect(Collectors.joining(", "));
    }

    /**
     * @param inv Invocation.
     */
    private boolean isFailure(Invocation inv) {
        return inv.status() == InvocationData.FAILURE || inv.status() == InvocationData.CRITICAL_FAILURE;
    }

    /**
     * @param status Status.
     */
    private String statusName(byte status) {
        if (status == InvocationData.OK)
            return "OK";

        if (status == InvocationData.FAILURE)
            return "FAIL";

        if (status == InvocationData.CRITICAL_FAILURE)
            return "CRITICAL_FAIL";

        if (status == InvocationData.MISSING)
            return "MISSING";

        if (status == InvocationData.IGNORED)
            return "IGNORED";

        if (Invocation.isMutedOrIgnored(status))
            return "MUTED_OR_IGNORED";

        return String.valueOf(status);
    }
}
