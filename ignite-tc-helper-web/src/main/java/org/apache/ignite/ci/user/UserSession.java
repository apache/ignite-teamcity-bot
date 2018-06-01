package org.apache.ignite.ci.user;

import org.apache.ignite.ci.db.Persisted;

@Persisted
public class UserSession {
    public String sessId;

    public String username;

    public Long loginTs;

    public Long lastActiveTs;

    public byte[] userKeyUnderToken;
}
