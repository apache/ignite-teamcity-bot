package org.apache.ignite.ci.detector;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.List;

public class IssuesStorage {
    public static final String ISSUES = "issues";
    private Ignite ignite;

    public IssuesStorage(Ignite ignite) {
        this.ignite = ignite;
    }

    IgniteCache<IssueKey, Issue> cache() {
        return ignite.getOrCreateCache(ISSUES);
    }

    public List<Issue> all() {
        List<Issue> res = new ArrayList<>();

        for (Cache.Entry<IssueKey, Issue> next : cache()) {
            if (next.getValue().issueKey() == null)
                continue;

            res.add(next.getValue());
        }

        return res;
    }

    public boolean needNotify(IssueKey issueKey, String to) {
        Issue issue = cache().get(issueKey);
        if (issue == null)
            return false;

        boolean add = issue.addressNotified.add(to);

        if (add)
            cache().put(issueKey, issue);

        return add;
    }
}
