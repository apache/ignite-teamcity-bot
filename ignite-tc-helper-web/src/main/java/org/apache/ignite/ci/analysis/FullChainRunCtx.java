package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.Build;

/**
 * Created by dpavlov on 20.09.2017
 */
public class FullChainRunCtx {
    private Build results;
    private List<FullBuildRunContext> list = new ArrayList<>();

    public FullChainRunCtx(Build results, List<FullBuildRunContext> list) {
        this.results = results;
        this.list = list;
    }

    public int buildProblems() {
        return (int)list.stream().filter(FullBuildRunContext::hasNontestBuildProblem).count();
    }

    public List<FullBuildRunContext> suites() {
        return list;
    }

    public String suiteName() {
        return results.suiteName();
    }

    public int failedTests() {
        return list.stream().mapToInt(FullBuildRunContext::failedTests).sum();
    }

    public int mutedTests() {
        return list.stream().mapToInt(FullBuildRunContext::mutedTests).sum();
    }

    public int totalTests() {
        return list.stream().mapToInt(FullBuildRunContext::totalTests).sum();
    }

    public Stream<FullBuildRunContext> failedChildSuites() {
        return suites().stream().filter(context -> {
            return context.failedTests() != 0 || context.hasAnyBuildProblemExceptTestOrSnapshot();
        });
    }
}
