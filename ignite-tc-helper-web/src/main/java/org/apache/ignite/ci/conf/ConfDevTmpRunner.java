package org.apache.ignite.ci.conf;

import com.google.gson.Gson;

/**
 * Created by Дмитрий on 05.11.2017.
 */
public class ConfDevTmpRunner {
    public static void main(String[] args) {
        BranchesTracked tracked = new BranchesTracked();
        BranchTracked branchTracked = new BranchTracked();
        branchTracked.id = "master";
        ChainAtServerTracked e = new ChainAtServerTracked();
        e.serverId = "public";
        e.suiteId = "Ignite20Tests_RunAll";
        e.branchForRest = "<default>";
        branchTracked.chains.add(e);

        tracked.branches.add(branchTracked);
        String s = new Gson().toJson(tracked);
        System.out.println(s);
    }
}
/* example of file:
{
  "branches": [
    {
      "id": "master",
      "chains": [
        {
          "serverId": "public",
          "suiteId": "Ignite20Tests_RunAll",
          "branchForRest": "\u003cdefault\u003e"
        },
        {
          "serverId": "private",
          "suiteId": "id8xIgniteGridGainTests_RunAll",
          "branchForRest": "\u003cdefault\u003e"
        }
      ]
    },
    {
      "id": "ignite-2.3",
      "chains": [
        {
          "serverId": "public",
          "suiteId": "Ignite20Tests_RunAll",
          "branchForRest": "ignite-2.3"
        },
        {
          "serverId": "private",
          "suiteId": "id8xIgniteGridGainTests_RunAll",
          "branchForRest": "ignite-2.3"
        }
      ]
    }
  ]
}
 */
