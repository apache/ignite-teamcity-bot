package org.apache.ignite.ci.web.model;

public class UserMenuResult extends SimpleResult {
    public String username;
    public boolean authorizedState;

    public UserMenuResult(String result) {
        super(result);

        this.username = result;
    }
}
