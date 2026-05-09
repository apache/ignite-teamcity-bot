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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** Max cached log scanner chars to include in one log context block. */
    private static final int LOG_CONTEXT_CHAR_BUDGET = 12000;

    /** Max cached log scanner tail chars to include when all messages are too large. */
    private static final int LOG_CONTEXT_TAIL_CHAR_BUDGET = 5000;

    /** Max suggested local search commands. */
    private static final int SUGGESTED_SEARCHES_LIMIT = 5;

    /** Max lines to keep from TeamCity stdout/stderr details sections. */
    private static final int DETAILS_SECTION_TAIL_LINES = 25;

    /** Max chars to keep from TeamCity stdout/stderr details sections. */
    private static final int DETAILS_SECTION_TAIL_CHARS = 6000;

    /** Throwable class in a log line, with an optional message. */
    private static final Pattern THROWABLE_SIGNAL = Pattern.compile(
        "\\b((?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error))\\b(?:\\s*:\\s*([^\\r\\n]+))?");

    /** Inline stack frame tail after a compacted exception message. */
    private static final Pattern INLINE_STACK_TAIL = Pattern.compile("\\s+at\\s+[\\w.$]+\\([^)]*\\).*$");

    /** Java source location in stack trace. */
    private static final Pattern JAVA_SOURCE_LOCATION = Pattern.compile("\\(([^():]+\\.java):(\\d+)\\)");

    /** String compactor. */
    private final IStringCompactor compactor;

    /**
     * @param compactor String compactor.
     */
    public TestFailuresAiPromptBuilder(IStringCompactor compactor) {
        this.compactor = compactor;
    }

    /**
     * @param maxDetailsChars Requested REST limit.
     * @return Safe REST limit for a single TeamCity failure details block.
     */
    public static int restMaxDetailsChars(@Nullable Integer maxDetailsChars) {
        if (maxDetailsChars == null || maxDetailsChars <= 0)
            return DFLT_MAX_DETAILS_CHARS;

        return Math.min(maxDetailsChars, DFLT_MAX_DETAILS_CHARS);
    }

    /**
     * @param tcIgnited TeamCity facade.
     * @param ctx Full chain context.
     * @param baseBranchTc Base branch for failure-rate history.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means no limit.
     */
    @Nonnull public String buildPrompt(ITeamcityIgnited tcIgnited, FullChainRunCtx ctx,
        @Nullable String baseBranchTc, int maxDetailsChars) {
        return buildPrompt(tcIgnited, ctx, baseBranchTc, maxDetailsChars, null, null);
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
        return buildPrompt(tcIgnited, ctx, baseBranchTc, maxDetailsChars, testNameFilter, null);
    }

    /**
     * @param tcIgnited TeamCity facade.
     * @param ctx Full chain context.
     * @param baseBranchTc Base branch for failure-rate history.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means no limit.
     * @param testNameFilter Optional full test name filter.
     * @param suiteIdFilter Optional suite id filter.
     */
    @Nonnull public String buildPrompt(ITeamcityIgnited tcIgnited, FullChainRunCtx ctx,
        @Nullable String baseBranchTc, int maxDetailsChars, @Nullable String testNameFilter,
        @Nullable String suiteIdFilter) {
        StringBuilder res = new StringBuilder();

        String normalizedBaseBranch = normalizeBranch(baseBranchTc);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        appendHeader(res, tcIgnited, ctx, normalizedBaseBranch);

        AtomicInteger suiteCnt = new AtomicInteger();
        AtomicInteger testCnt = new AtomicInteger();

        List<MultBuildRunCtx> failedSuites = ctx.suites()
            .filter(suite -> !suite.isComposite())
            .filter(MultBuildRunCtx::isFailed)
            .filter(suite -> suiteIdFilter == null || suiteIdFilter.equals(suite.suiteId()))
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
                appendSuiteFailure(res, suite);
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
        res.append("Use this compact investigation brief to find the likely root cause with minimal assumptions.\n\n");

        res.append("## Chain Context\n");
        appendLine(res, "TeamCity server", tcIgnited.serverCode());
        appendLine(res, "Chain", ctx.suiteName());
        appendLine(res, "Suite id", ctx.suiteId());
        appendLine(res, "Branch", ctx.branchName());
        appendLine(res, "Base branch for history", normalizedBaseBranch);
        appendLine(res, "Entry build id", String.valueOf(ctx.getSuiteBuildId()));
        appendLine(res, "Entry build", tcIgnited.host() + "viewLog.html?buildId=" + ctx.getSuiteBuildId());
        appendLine(res, "Duration", ctx.getDurationPrintable(suite -> true));
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

        res.append("## Failed Suite Context\n");
        res.append("Suite: ").append(nullToUnknown(suite.suiteName())).append('\n');
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
        List<Map.Entry<String, ITestLogCheckResult>> messages = suite.getLogsCheckResults()
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> entry.getValue() != null && entry.getValue().hasWarns())
            .collect(Collectors.toList());

        if (messages.isEmpty()) {
            appendLogAnalysisNote(res, suite);

            return;
        }

        res.append("Build log scanner messages:\n");

        for (Map.Entry<String, ITestLogCheckResult> entry : messages) {
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

        res.append("## Failure Signal\n");
        appendLine(res, "Full test name", fullName);
        appendLine(res, "Suite before colon", testSuitePart(fullName));
        appendLine(res, "Test after colon", testCasePart(fullName));
        appendProblems(res, suite);

        AtomicInteger invocationIdx = new AtomicInteger();

        test.getInvocationsStream()
            .filter(Objects::nonNull)
            .filter(invocation -> invocation.isFailedButNotMuted(compactor))
            .forEach(invocation -> appendInvocation(res, tcIgnited, suite, invocation, fullName,
                invocationIdx.incrementAndGet(), maxDetailsChars));

        appendRelevantLogChecks(res, suite, fullName);
        appendSuggestedLocalSearches(res, suite, fullName);

        res.append("\n## Changed Files / PR Context\n");
        res.append("Changed files and patches are not available in this cached TeamCity bot context. ");
        res.append("Inspect the local checkout diff/PR metadata if needed, then compare changed packages with the failing test/module.\n");

        res.append("\n## Test Context\n");
        appendLine(res, "Full test name", fullName);
        appendLine(res, "Suite before colon", testSuitePart(fullName));
        appendLine(res, "Test after colon", testCasePart(fullName));
        appendLine(res, "Short suite", ShortTestFailureUi.extractSuite(Strings.nullToEmpty(testSuitePart(fullName))));
        appendLine(res, "Short test", extractShortTest(fullName));
        appendLine(res, "Failures in loaded context", String.valueOf(test.failuresCount()));
        appendLine(res, "Investigated in TeamCity", String.valueOf(test.isInvestigated()));
        appendLine(res, "Possible blocker note", test.getPossibleBlockerComment(testHist));

        if (testHist != null) {
            res.append("\n## History\n");
            appendLine(res, "Base branch test failure rate", testHist.getFailPercentPrintable() + "%");
            appendLine(res, "Base branch flaky", String.valueOf(testHist.isFlaky()));
            appendLine(res, "Base branch latest runs", String.valueOf(testHist.getLatestRunResults()));
            appendLine(res, "Break boundary", breakBoundary(testHist));
            appendLine(res, "Recent execution history", recentHistory(testHist, 12));
        }

        appendInvestigationInstructions(res, masterChangeCauseInstruction(suite.branchName(), testHist));

        res.append('\n');
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     */
    private void appendSuiteFailure(StringBuilder res, MultBuildRunCtx suite) {
        res.append("## Failure Signal\n");
        res.append("No failed non-muted test was reported for this suite. Investigate this as a suite/build-level failure.\n");
        appendProblems(res, suite);
        appendEmptyDetailsDiagnosis(res, suite);

        res.append("\n## Log Context\n");
        appendLogChecks(res, suite);
        appendSuggestedLocalSearches(res, suite, suite.suiteName());

        res.append("\n## Changed Files / PR Context\n");
        res.append("Changed files and patches are not available in this cached TeamCity bot context. ");
        res.append("Inspect the local checkout diff/PR metadata if needed, then compare changed packages with the failed suite/module.\n");

        res.append("\n## Test Context\n");
        appendLine(res, "Suite id", suite.suiteId());
        appendLine(res, "Suite name", suite.suiteName());
        appendLine(res, "Failed tests reported", String.valueOf(suite.failedTests()));
        appendLine(res, "Total tests", String.valueOf(suite.totalTests()));

        appendInvestigationInstructions(res, null);

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
        ITest invocation, @Nullable String fullName, int idx, int maxDetailsChars) {
        int actualBuildId = invocation.getActualBuildId() > 0 ? invocation.getActualBuildId() : suite.getBuildId();

        res.append("Invocation ").append(idx).append(":\n");
        appendLine(res, "Full test name", fullName);
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
            appendEmptyDetailsDiagnosis(res, suite);
        else {
            String cleanedDetails = cleanDetails(details);

            res.append("First real failure signal:\n");
            res.append("```text\n");
            res.append(limit(firstFailureSignal(cleanedDetails), 6000));
            res.append("\n```\n");

            String summary = relevantStdoutStderrSummary(cleanedDetails, duration);

            if (!Strings.isNullOrEmpty(summary)) {
                res.append("Relevant stdout/stderr:\n");
                res.append(summary);
            }

            String detailsTails = detailsTailsForPrompt(cleanedDetails);

            if (!Strings.isNullOrEmpty(detailsTails)) {
                res.append("<details>\n");
                res.append("<summary>Raw TeamCity stdout/stderr tail (secondary)</summary>\n\n");
                res.append("```text\n");
                res.append(limit(detailsTails, Math.min(maxDetailsChars, DETAILS_SECTION_TAIL_CHARS)));
                res.append("\n```\n");
                res.append("</details>\n");
            }
        }
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     * @param fullName Full test name.
     */
    private void appendRelevantLogChecks(StringBuilder res, MultBuildRunCtx suite, @Nullable String fullName) {
        List<List<String>> relevantBlocks = new ArrayList<>();
        List<List<String>> otherBlocks = new ArrayList<>();

        suite.getLogsCheckResults()
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> entry.getValue() != null)
            .forEach(entry -> {
                ITestLogCheckResult checkRes = entry.getValue();

                if (!checkRes.hasWarns())
                    return;

                List<String> block = logCheckBlock(entry);

                if (isSameTestLog(entry.getKey(), fullName))
                    relevantBlocks.add(block);
                else
                    otherBlocks.add(block);
            });

        if (relevantBlocks.isEmpty() && otherBlocks.isEmpty()) {
            res.append("\n## Log Context\n");
            appendLogAnalysisNote(res, suite);

            return;
        }

        List<List<String>> allBlocks = new ArrayList<>(relevantBlocks);
        allBlocks.addAll(otherBlocks);

        res.append("\n## Log Context\n");
        if (logBlocksChars(allBlocks) <= LOG_CONTEXT_CHAR_BUDGET) {
            res.append("Cached build-log scanner messages. Full cached message set is included because it is small enough:\n");
            appendLogBlocks(res, allBlocks);

            return;
        }

        if (!relevantBlocks.isEmpty()) {
            res.append("Cached log grep for the failing test:\n");
            appendLogBlocks(res, relevantBlocks);

            if (!otherBlocks.isEmpty()) {
                res.append("\nAdditional cached build-log scanner messages near the end of the processed log context");
                res.append(" (truncated to keep the prompt compact):\n");
                appendLogBlocks(res, tailLogBlocks(otherBlocks));
            }

            return;
        }

        res.append("Cached build-log scanner messages are too large to include fully. ");
        res.append("Showing messages near the end of the processed log context:\n");
        appendLogBlocks(res, tailLogBlocks(allBlocks));
    }

    /**
     * @param res Result builder.
     * @param blocks Log check blocks.
     */
    private void appendLogBlocks(StringBuilder res, List<List<String>> blocks) {
        res.append("```text\n");

        for (List<String> block : blocks) {
            for (String line : block)
                res.append(line).append('\n');

            res.append('\n');
        }

        res.append("```\n");
    }

    /**
     * @param entry Log check entry.
     * @return Prompt lines for the entry.
     */
    private List<String> logCheckBlock(Map.Entry<String, ITestLogCheckResult> entry) {
        ITestLogCheckResult checkRes = entry.getValue();
        List<String> block = new ArrayList<>();

        block.add("Log grep for " + entry.getKey() + " (log size " + checkRes.getLogSizeBytes() + " bytes):");
        block.addAll(checkRes.getWarns());

        return block;
    }

    /**
     * @param blocks Log check blocks.
     * @return Approximate char count.
     */
    private int logBlocksChars(List<List<String>> blocks) {
        return blocks.stream()
            .flatMap(List::stream)
            .mapToInt(line -> line.length() + 1)
            .sum();
    }

    /**
     * @param blocks Log check blocks.
     * @return Tail blocks fitting the tail budget.
     */
    private List<List<String>> tailLogBlocks(List<List<String>> blocks) {
        List<List<String>> res = new ArrayList<>();
        int chars = 0;

        for (int i = blocks.size() - 1; i >= 0; i--) {
            List<String> block = blocks.get(i);
            int blockChars = logBlocksChars(Collections.singletonList(block));

            if (!res.isEmpty() && chars + blockChars > LOG_CONTEXT_TAIL_CHAR_BUDGET)
                break;

            res.add(0, block);
            chars += blockChars;
        }

        return res;
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     * @param fullName Full TeamCity test name.
     */
    private void appendSuggestedLocalSearches(StringBuilder res, MultBuildRunCtx suite, @Nullable String fullName) {
        Set<String> commands = new LinkedHashSet<>();

        String testRegex = testSearchRegex(fullName);

        if (!Strings.isNullOrEmpty(testRegex))
            addSearch(commands, testRegex, ".");

        List<String> warns = suite.getLogsCheckResults()
            .flatMap(map -> map.entrySet().stream())
            .filter(entry -> entry.getValue() != null)
            .flatMap(entry -> entry.getValue().getWarns().stream())
            .collect(Collectors.toList());

        String logRegex = logSearchRegex(warns);

        if (!Strings.isNullOrEmpty(logRegex))
            addSearch(commands, logRegex, ".");

        String className = simpleClassName(testCasePart(fullName));

        if (!Strings.isNullOrEmpty(className) && warns.stream().anyMatch(warn -> warn.contains("AssertionError")))
            addSearch(commands, escapeRgTerm(className) + "|AssertionError", "modules");

        if (commands.isEmpty())
            return;

        res.append("\n## Suggested Local Searches\n");

        commands.stream().limit(SUGGESTED_SEARCHES_LIMIT).forEach(cmd -> res.append("- ").append(cmd).append('\n'));
    }

    /**
     * @param commands Commands.
     * @param regex Search regex.
     * @param path Search path.
     */
    private void addSearch(Set<String> commands, String regex, String path) {
        commands.add("rg -n \"" + regex.replace("\"", "\\\"") + "\" " + path);
    }

    /**
     * @param fullName Full TeamCity test name.
     * @return Local search regex for test class/method.
     */
    @Nullable private String testSearchRegex(@Nullable String fullName) {
        String testCase = testCasePart(fullName);
        String className = simpleClassName(testCase);
        String methodName = methodName(testCase);

        if (!Strings.isNullOrEmpty(methodName) && !Strings.isNullOrEmpty(className))
            return escapeRgTerm(methodName) + "|" + escapeRgTerm(className);

        if (!Strings.isNullOrEmpty(className))
            return escapeRgTerm(className);

        return null;
    }

    /**
     * @param warns Log scanner messages from the current compacted API.
     * @return Search regex for notable log messages.
     */
    @Nullable String logSearchRegex(List<String> warns) {
        String joined = String.join("\n", warns);
        List<String> terms = new ArrayList<>();

        terms.addAll(throwableSearchTerms(warns));

        addIfPresent(terms, joined, "SYSTEM_WORKER_TERMINATION");
        addIfPresent(terms, joined, "Critical system error detected");
        addIfPresent(terms, joined, "AssertionError");
        addIfPresent(terms, joined, "OutOfMemoryError");
        addIfPresent(terms, joined, "Process exited with code");

        return terms.stream().distinct().map(this::escapeRgTerm).collect(Collectors.joining("|"));
    }

    /**
     * @param warns Log scanner messages from the current compacted API.
     * @return Throwable class names and messages worth searching in the checkout.
     */
    private List<String> throwableSearchTerms(List<String> warns) {
        Set<String> terms = new LinkedHashSet<>();

        for (String warn : warns) {
            if (Strings.isNullOrEmpty(warn))
                continue;

            for (String line : warn.split("\\r?\\n")) {
                Matcher matcher = THROWABLE_SIGNAL.matcher(line);

                while (matcher.find()) {
                    String throwableCls = simpleThrowableName(matcher.group(1));
                    String msg = normalizeSearchMessage(matcher.group(2));

                    if (!Strings.isNullOrEmpty(throwableCls))
                        terms.add(throwableCls);
                    if (!Strings.isNullOrEmpty(msg))
                        terms.add(msg);
                }
            }
        }

        return new ArrayList<>(terms);
    }

    /**
     * @param cls Throwable class name.
     * @return Simple throwable class name.
     */
    @Nullable private String simpleThrowableName(@Nullable String cls) {
        if (Strings.isNullOrEmpty(cls))
            return null;

        int idx = cls.lastIndexOf('.');

        return idx >= 0 ? cls.substring(idx + 1) : cls;
    }

    /**
     * @param msg Exception/assertion message.
     * @return Searchable message.
     */
    @Nullable private String normalizeSearchMessage(@Nullable String msg) {
        if (Strings.isNullOrEmpty(msg))
            return null;

        String normalized = INLINE_STACK_TAIL.matcher(msg).replaceFirst("")
            .replaceAll("\\s+", " ")
            .trim();

        return normalized.length() < 5 ? null : normalized;
    }

    /**
     * @param terms Terms.
     * @param text Text.
     * @param term Term.
     */
    private void addIfPresent(List<String> terms, String text, String term) {
        if (text.contains(term))
            terms.add(term);
    }

    /**
     * @param fullTestName Test name with package/class/method.
     * @return Simple class name.
     */
    @Nullable private String simpleClassName(@Nullable String fullTestName) {
        if (Strings.isNullOrEmpty(fullTestName))
            return null;

        int methodSep = fullTestName.lastIndexOf('.');

        if (methodSep < 0)
            return fullTestName;

        int classSep = fullTestName.lastIndexOf('.', methodSep - 1);

        return fullTestName.substring(classSep + 1, methodSep);
    }

    /**
     * @param fullTestName Test name with package/class/method.
     * @return Method name.
     */
    @Nullable private String methodName(@Nullable String fullTestName) {
        if (Strings.isNullOrEmpty(fullTestName))
            return null;

        int methodSep = fullTestName.lastIndexOf('.');

        if (methodSep < 0 || methodSep + 1 >= fullTestName.length())
            return null;

        return fullTestName.substring(methodSep + 1);
    }

    /**
     * @param term Plain search term.
     * @return Regex-safe search term.
     */
    private String escapeRgTerm(String term) {
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < term.length(); i++) {
            char ch = term.charAt(i);

            if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0)
                res.append('\\');

            res.append(ch);
        }

        return res.toString();
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     */
    private void appendLogAnalysisNote(StringBuilder res, MultBuildRunCtx suite) {
        long pending = suite.pendingLogChecksCount();

        if (pending > 0) {
            res.append("Build log analysis was requested for this prompt, but ");
            res.append(pending).append(" log task(s) did not finish before the prompt timeout. ");
            res.append("The prompt may miss log grep context; retry later to use cached results.\n");

            return;
        }

        if (suite.logChecksStartedCount() > 0) {
            res.append("Build log analysis completed or failed without matching scanner messages for this scope. ");
            res.append("Raw 200-500 line windows around test start/end are not available in the current bot cache.\n");

            return;
        }

        res.append("Build log analysis was not available for this prompt. ");
        res.append("Raw 200-500 line windows around test start/end are not available in the current bot cache.\n");
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
     * @param fullName Full test name.
     */
    @Nullable private String testSuitePart(@Nullable String fullName) {
        if (fullName == null)
            return null;

        int colon = fullName.indexOf(':');

        if (colon < 0)
            return fullName;

        return fullName.substring(0, colon).trim();
    }

    /**
     * @param fullName Full test name.
     */
    @Nullable private String testCasePart(@Nullable String fullName) {
        if (fullName == null)
            return null;

        int colon = fullName.indexOf(':');

        if (colon < 0 || colon + 1 >= fullName.length())
            return null;

        return fullName.substring(colon + 1).trim();
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
            + "Increase maxDetailsChars up to the server cap if more details are needed.";
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
     * @param details Cleaned TeamCity details.
     * @return Short stdout/stderr tails for prompt context.
     */
    String detailsTailsForPrompt(String details) {
        String[] lines = details.split("\\r?\\n");
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (!isDetailsSectionHeader(lines[i]))
                continue;

            int end = i + 1;

            while (end < lines.length && !isDetailsSectionHeader(lines[end]))
                end++;

            appendSectionTail(res, lines, i, end);
        }

        return res.toString().trim();
    }

    /**
     * @param details Cleaned TeamCity details.
     * @param duration Test duration in millis.
     * @return Compact stdout/stderr interpretation.
     */
    String relevantStdoutStderrSummary(String details, @Nullable Integer duration) {
        List<String> bullets = new ArrayList<>();

        if (duration != null)
            bullets.add("Test started and failed within ~" + duration + " ms.");

        String location = firstJavaSourceLocation(details);

        if (!Strings.isNullOrEmpty(location)) {
            if (details.contains("Assertion"))
                bullets.add("Assertion at " + location + ".");
            else
                bullets.add("Failure stack points to " + location + ".");
        }

        if (igniteNodeStartedAndStopped(details))
            bullets.add("Ignite node started and stopped normally.");

        if (!hasTimeoutOrCrashSignal(details))
            bullets.add("No timeout or crash signal.");

        return bullets.stream().map(bullet -> "- " + bullet + '\n').collect(Collectors.joining());
    }

    /**
     * @param details Details.
     * @return First Java source location.
     */
    @Nullable private String firstJavaSourceLocation(String details) {
        Matcher matcher = JAVA_SOURCE_LOCATION.matcher(details);

        return matcher.find() ? matcher.group(1) + ":" + matcher.group(2) : null;
    }

    /**
     * @param details Details.
     * @return {@code true} if stdout/stderr contains a normal Ignite lifecycle signal.
     */
    private boolean igniteNodeStartedAndStopped(String details) {
        String lower = details.toLowerCase();

        boolean started = lower.contains("ignite node started")
            || lower.contains("topology snapshot")
            || lower.contains(">>> started cache");

        boolean stopped = lower.contains("ignite node stopped")
            || lower.contains("stopping grid")
            || lower.contains("grid stopped")
            || lower.contains("ignite instance stopped");

        return started && stopped;
    }

    /**
     * @param details Details.
     * @return {@code true} if details contains timeout/crash/process-failure signal.
     */
    private boolean hasTimeoutOrCrashSignal(String details) {
        String lower = details.toLowerCase();

        return lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("outofmemoryerror")
            || lower.contains("hs_err")
            || lower.contains("jvm crash")
            || lower.contains("process exited")
            || lower.contains("exit code");
    }

    /**
     * @param line Details line.
     * @return {@code true} if line starts a stdout/stderr details section.
     */
    private boolean isDetailsSectionHeader(String line) {
        return "------- Stdout: -------".equals(line) || "------- Stderr: -------".equals(line);
    }

    /**
     * @param res Result.
     * @param lines Details lines.
     * @param headerIdx Header line index.
     * @param end Exclusive section end.
     */
    private void appendSectionTail(StringBuilder res, String[] lines, int headerIdx, int end) {
        int start = Math.max(headerIdx + 1, end - DETAILS_SECTION_TAIL_LINES);

        if (start >= end)
            return;

        if (res.length() > 0)
            res.append('\n');

        res.append(lines[headerIdx]).append('\n');

        if (start > headerIdx + 1)
            res.append("... skipped ").append(start - headerIdx - 1).append(" earlier lines ...\n");

        for (int i = start; i < end; i++)
            res.append(lines[i]).append('\n');
    }

    /**
     * @param details Details from TeamCity.
     */
    private String firstFailureSignal(String details) {
        String normalized = details
            .replace(" Caused by: ", "\nCaused by: ")
            .replace(" at ", "\n    at ");

        String[] lines = normalized.split("\\r?\\n");
        int start = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.contains("Exception")
                || line.contains("Assertion")
                || line.contains("Error")
                || line.contains("Caused by:")
                || line.contains("Test failed")
                || line.contains("Failed")) {
                start = i;

                break;
            }
        }

        if (start < 0)
            return limit(normalized, 6000);

        StringBuilder res = new StringBuilder();

        for (int i = start; i < lines.length && i < start + 80; i++)
            res.append(lines[i]).append('\n');

        return res.toString().trim();
    }

    /**
     * @param res Result builder.
     * @param suite Suite context.
     */
    private void appendEmptyDetailsDiagnosis(StringBuilder res, MultBuildRunCtx suite) {
        res.append("TeamCity failure details: <empty>\n");
        res.append("Likely empty-details category: ");

        if (suite.hasTimeoutProblem())
            res.append("timeout or test process killed before reporting a stack trace");
        else if (suite.hasJvmCrashProblem())
            res.append("JVM crash");
        else if (suite.hasOomeProblem())
            res.append("OOM / process killed");
        else if (suite.hasExitCodeProblem())
            res.append("test process exited with non-zero code");
        else if (suite.hasProblemNonByFailedTest())
            res.append("build problem without a test stack");
        else if (suite.failedTests() == 0)
            res.append("orphaned test/reporting issue");
        else
            res.append("reporting issue or TeamCity did not persist details");

        res.append(".\n");
    }

    /**
     * @param res Result builder.
     */
    private void appendInvestigationInstructions(StringBuilder res, @Nullable String masterCauseInstruction) {
        res.append("\n## Investigation Instructions\n");
        res.append("- Start from the First real failure signal and nearest project frame.\n");
        res.append("- Search for the failing class, method, assertion text, and error message in the checkout.\n");
        res.append("- Compare test history and suite history separately before calling it flaky.\n");
        if (!Strings.isNullOrEmpty(masterCauseInstruction))
            res.append("- ").append(masterCauseInstruction).append('\n');
        res.append("- Classify the cause as exactly one of: caused by current change; pre-existing flaky test; ");
        res.append("environmental/infra; product bug exposed by test; inconclusive.\n");

        res.append("\n## Required Final Answer\n");
        res.append("- Likely root cause.\n");
        res.append("- Confidence level.\n");
        res.append("- Files/classes/methods to inspect.\n");
        res.append("- Minimal fix or mitigation.\n");
        res.append("- Whether retry is justified.\n");
        res.append("- Extra artifact/log that would confirm the diagnosis.\n");
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
        BuildBoundary boundary = findBreakBoundary(hist);

        if (boundary == null)
            return null;

        return "last OK build " + boundary.lastOk.buildId()
            + ", first later failure build " + boundary.firstBadAfterOk.buildId()
            + ", change state at first failure " + boundary.firstBadAfterOk.changesState();
    }

    /**
     * @param branch Branch.
     * @param hist Run history.
     * @return Master branch cause interpretation instruction.
     */
    @Nullable private String masterChangeCauseInstruction(@Nullable String branch, @Nullable IRunHistory hist) {
        if (hist == null || !"master".equals(normalizeBranch(branch)))
            return null;

        BuildBoundary boundary = findBreakBoundary(hist);

        if (boundary == null)
            return null;

        return "For master builds, interpret \"caused by current change\" as caused by a recent master change " +
            "between last OK build " + boundary.lastOk.buildId() + " and first failing build " +
            boundary.firstBadAfterOk.buildId() + ".";
    }

    /**
     * @param hist Run history.
     * @return Break boundary.
     */
    @Nullable private BuildBoundary findBreakBoundary(IRunHistory hist) {
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

        return new BuildBoundary(lastOk, firstBadAfterOk);
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

    /** Break boundary. */
    private static class BuildBoundary {
        /** Last successful run before break. */
        private final Invocation lastOk;

        /** First failing run after success. */
        private final Invocation firstBadAfterOk;

        /**
         * @param lastOk Last successful run before break.
         * @param firstBadAfterOk First failing run after success.
         */
        private BuildBoundary(Invocation lastOk, Invocation firstBadAfterOk) {
            this.lastOk = lastOk;
            this.firstBadAfterOk = firstBadAfterOk;
        }
    }
}
