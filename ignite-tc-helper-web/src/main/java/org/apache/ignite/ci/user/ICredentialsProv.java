package org.apache.ignite.ci.user;

import com.google.common.base.Strings;

import javax.servlet.http.HttpServletRequest;

public interface ICredentialsProv {
    String _KEY = ICredentialsProv.class.getName();

    //note it will not work for PermitAll Methods
    static ICredentialsProv get(HttpServletRequest request) {
        return (ICredentialsProv) request.getAttribute(_KEY);
    }

    String getUser(String server);
    String getPassword(String server);

    default boolean hasAccess(String srvId) {
        return !Strings.isNullOrEmpty(getUser(srvId)) && !Strings.isNullOrEmpty(getPassword(srvId));
    }

    String getPrincipalId();

    byte[] getUserKey();
}
