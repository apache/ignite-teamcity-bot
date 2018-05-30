package org.apache.ignite.ci.detector;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailure;
import org.apache.ignite.ci.web.rest.model.current.TestFailuresSummary;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IssuesStorage {
    private Ignite ignite;

    public IssuesStorage(Ignite ignite) {
        this.ignite = ignite;
    }

    private IgniteCache<Object, Issue> cache( ) {
        return ignite.getOrCreateCache("issues");
    }

    public List<Issue> all() {
        List<Issue> res = new ArrayList<>();

        for (Cache.Entry<Object, Issue> next : cache()) {
            res.add(next.getValue());
        }

        return res;
    }

    public void registerNewIssues(TestFailuresSummary res) {
        List<ChainAtServerCurrentStatus> servers = res.servers;

        for (ChainAtServerCurrentStatus next : servers) {
            for (SuiteCurrentStatus suiteCurrentStatus : next.suites) {

                List<TestFailure> testFailures = suiteCurrentStatus.testFailures;
                for (TestFailure testFailure : testFailures) {

                    if (testFailure.problemRef != null) {
                        Issue val = new Issue();
                        val.objectId = testFailure.name;
                        val.displayType = testFailure.problemRef.name;

                        cache().put(testFailure.name, val);
                    }
                }
            }
        }
    }
}
