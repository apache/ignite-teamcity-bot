package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.analysis.FullBuildRunContext;
import org.apache.ignite.ci.analysis.FullChainRunCtx;

/**
 * Represent Run All chain results/ or RunAll+latest re-runs
 */
public class ChainCurrentStatus extends AbstractTestMetrics {
    public String serverName;

    public List<SuiteCurrentStatus> suites = new ArrayList<>();

    public void initFromContext(FullChainRunCtx ctx) {
        ctx.failedChildSuites().forEach(
            suite -> {
                final SuiteCurrentStatus suiteCurStatus = new SuiteCurrentStatus();
                suiteCurStatus.initFromContext(suite);
                this.suites.add(suiteCurStatus);
            }
        );
    }
}
