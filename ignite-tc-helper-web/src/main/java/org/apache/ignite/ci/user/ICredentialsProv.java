package org.apache.ignite.ci.user;

public interface ICredentialsProv {
    String getUser(String server);
    String getPassword(String server);
}
