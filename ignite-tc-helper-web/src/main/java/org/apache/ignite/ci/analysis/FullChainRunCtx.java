package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ignite.ci.analysis.FullSuiteRunContext;
import org.apache.ignite.ci.model.result.FullBuildInfo;

/**
 * Created by dpavlov on 20.09.2017
 */
public class FullChainRunCtx {
    private FullBuildInfo results;
    private List<FullSuiteRunContext> list = new ArrayList<>();

    public FullChainRunCtx(FullBuildInfo results, List<FullSuiteRunContext> list) {
        this.results = results;
        this.list = list;
    }

    public int buildProblems() {
        return (int)list.stream().filter(FullSuiteRunContext::hasNontestBuildProblem).count();
    }

    public List<FullSuiteRunContext> suites() {
        return list;
    }

    public String suiteName() {
        return results.suiteName();
    }

    public int failedTests() {
        return list.stream().mapToInt(FullSuiteRunContext::failedTests).sum();
    }

    public int mutedTests() {
        return list.stream().mapToInt(FullSuiteRunContext::mutedTests).sum();
    }

    public int totalTests() {
        return list.stream().mapToInt(FullSuiteRunContext::totalTests).sum();
    }

    public Stream<FullSuiteRunContext> failedChildSuites() {
        return suites().stream().filter(context -> {
            return context.failedTests() != 0 || context.hasAnyBuildProblemExceptTest();
        });
    }
}
