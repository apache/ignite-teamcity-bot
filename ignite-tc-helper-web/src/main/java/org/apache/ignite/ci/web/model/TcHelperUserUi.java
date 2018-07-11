package org.apache.ignite.ci.web.model;

import java.util.Map;
import java.util.TreeMap;
import org.apache.ignite.ci.user.TcHelperUser;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("weakerAccess")
public class TcHelperUserUi {
    public final String login;

    public List<CredentialsUi> data = new ArrayList<>();
    public Map<String, Boolean> subscribedAllToBranchFailures = new TreeMap<>();

    public String fullName;

    public String email;

    public TcHelperUserUi(TcHelperUser user, List<String> allTrackedBranches) {
        login = user.username;
        fullName = user.fullName;
        email = user.email;
        allTrackedBranches.forEach(
            branchId -> subscribedAllToBranchFailures.put(branchId, user.isSubscribed(branchId))
        );
    }
}
