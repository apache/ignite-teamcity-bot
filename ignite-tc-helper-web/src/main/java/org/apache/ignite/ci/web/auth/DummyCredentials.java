package org.apache.ignite.ci.web.auth;

import com.google.common.base.Preconditions;
import org.apache.ignite.ci.util.Base64Util;

import javax.servlet.http.HttpServletRequest;

public class DummyCredentials implements ICredentialsProv {

    private final String user;
    private final String password;

    public DummyCredentials(String user, String password) {


        this.user = user;
        this.password = password;
    }

    public static DummyCredentials create(HttpServletRequest request) {
        final String user = (String) request.getAttribute( "principal");

        Preconditions.checkNotNull(user, "User should be defined");
        System.out.println("Username for TC " + user);

        final String password = (String) request.getAttribute( "password");

        DummyCredentials credentials = new DummyCredentials(user, password);

        return credentials;
    }

    @Override
    public String getUser(String server) {
        return user;
    }

    @Override
    public String getPassword(String server) {
        return password;
    }
}
