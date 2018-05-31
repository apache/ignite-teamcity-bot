package org.apache.ignite.ci.web.rest;

import com.google.common.base.Preconditions;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.auth.LoginResponse;
import org.apache.ignite.ci.web.auth.UserSession;

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
    public static final String USER_SESSIONS = "UserSessions";

    @Context
    private ServletContext context;

    @POST
    @Path("login")
    @PermitAll
    public LoginResponse login(@FormParam("uname") String username,
                               @FormParam("psw") String password) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);
        Ignite ignite = CtxListener.getIgnite(context);

        IgniteCache<String, UserSession> userSessions = ignite.getOrCreateCache(USER_SESSIONS);

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
        userSession.ts = System.currentTimeMillis();

        userSessions.put(sessId, userSession);

        LoginResponse loginResponse = new LoginResponse();

        loginResponse.fullToken = sessId + ":" + token;

        System.out.println("Saved session id " + sessId);

        return loginResponse;
    }

}
