package org.apache.ignite.ci.web.rest.login;

import com.google.common.base.Preconditions;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.user.LoginResponse;
import org.apache.ignite.ci.user.UserSession;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;

@Path("login")
@Produces("application/json")
public class Login {
    public static final int TOKEN_LEN = 12;
    public static final int SESS_ID = 8;

    @Context
    private ServletContext context;

    @POST
    @Path("login")
    @PermitAll
    public LoginResponse login(@FormParam("uname") String username,
                               @FormParam("psw") String password) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);

        UserAndSessionsStorage users = CtxListener.getTcHelper(context).users();

        SecureRandom random = new SecureRandom();
        byte[] pref = random.generateSeed(TOKEN_LEN);
        String token = DatatypeConverter.printHexBinary(pref);

        byte[] sessIdBytes = random.generateSeed(SESS_ID);
        String sessId = DatatypeConverter.printHexBinary(sessIdBytes);

        String encode = PasswordEncoder.encode(password);

        UserSession userSession = new UserSession();
        userSession.username = username;
        userSession.encodedPassword = encode;
        userSession.sessId = sessId;

        userSession.loginTs = System.currentTimeMillis();

        users.addSession(sessId, userSession);

        LoginResponse loginResponse = new LoginResponse();

        loginResponse.fullToken = sessId + ":" + token;

        System.out.println("Saved session id " + sessId);

        return loginResponse;
    }

}
