package org.apache.ignite.ci.user;

import com.google.common.base.Strings;

public interface ICredentialsProv {
    String getUser(String server);
    String getPassword(String server);

    default boolean hasAccess(String srvId) {
        return !Strings.isNullOrEmpty(getUser(srvId)) && !Strings.isNullOrEmpty(getPassword(srvId));
    }
}
