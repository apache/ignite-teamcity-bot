package org.apache.ignite.ci.runners;

import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.IssuesStorage;

public class ClientTmpHelper {
    public static void main(String[] args) {

        Ignite ignite = TcHelperDb.startClient();
        ignite.cache(IssuesStorage.ISSUES).clear();
        ignite.close();
    }
}
