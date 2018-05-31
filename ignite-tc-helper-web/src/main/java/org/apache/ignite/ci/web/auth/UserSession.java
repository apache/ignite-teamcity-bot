package org.apache.ignite.ci.web.auth;

import org.apache.ignite.ci.db.Persisted;

@Persisted
public class UserSession {
    public String sessId;

    public String username;

    public String encodedPassword;

    public Long ts;
}
