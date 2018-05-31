package org.apache.ignite.ci.web.auth;

public interface ICredentialsProv {
    String getUser(String server);
    String getPassword(String server);
}
