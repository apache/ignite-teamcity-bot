package org.apache.ignite.ci.runners;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.user.UserSession;

public class ClientTmpHelper {
    public static void main(String[] args) {
        Ignite ignite = TcHelperDb.startClient();

        ignite.cache(IssuesStorage.ISSUES).clear();
        //ignite.cache(UserAndSessionsStorage.USERS).destroy();
        Object dpavlov = ignite.cache(UserAndSessionsStorage.USERS).get("dpavlov");

        IgniteCache<Object, Object> cache = ignite.cache(IssuesStorage.ISSUES);
        cache.forEach(
            issue->{
                Object key = issue.getKey();
                Issue value = (Issue)issue.getValue();
                 // value.addressNotified.clear();

                cache.put(key, value);

            }
        );

        ignite.close();
    }
}
