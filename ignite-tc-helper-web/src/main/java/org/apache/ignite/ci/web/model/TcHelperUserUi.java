package org.apache.ignite.ci.web.model;

import org.apache.ignite.ci.user.TcHelperUser;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("weakerAccess")
public class TcHelperUserUi {
    public final String login;

    public List<CredentialsUi> data = new ArrayList<>();

    public String fullName;

    public String email;

    public TcHelperUserUi(TcHelperUser user) {
        login = user.username;
        fullName = user.fullName;
        email = user.email;
    }
}
