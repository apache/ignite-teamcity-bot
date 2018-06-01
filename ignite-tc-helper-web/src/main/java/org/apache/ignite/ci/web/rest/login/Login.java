package org.apache.ignite.ci.web.rest.login;

import com.google.common.base.Preconditions;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.util.CryptUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.user.LoginResponse;
import org.apache.ignite.ci.user.UserSession;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.xml.bind.DatatypeConverter;
import java.security.SecureRandom;
import java.util.Arrays;

@Path("login")
@Produces("application/json")
public class Login {
    public static final int TOKEN_LEN = 12;
    public static final int SESS_ID_LEN = 8;
    public static final int SALT_LEN = 16;

    @Context
    private ServletContext context;

    @POST
    @Path("login")
    @PermitAll
    public LoginResponse login(@FormParam("uname") String username,
                               @FormParam("psw") String password) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);

        ITcHelper tcHelper = CtxListener.getTcHelper(context);
        UserAndSessionsStorage users = tcHelper.users();

        return doLogin(username, password, users);
    }

    public LoginResponse doLogin(@FormParam("uname") String username,
                                 @FormParam("psw") String password,
                                 UserAndSessionsStorage users) {
        SecureRandom random = new SecureRandom();
        byte[] pref = random.generateSeed(TOKEN_LEN);
        String token = DatatypeConverter.printHexBinary(pref);

        byte[] sessIdBytes = random.generateSeed(SESS_ID_LEN);
        String sessId = DatatypeConverter.printHexBinary(sessIdBytes);

        String encode = PasswordEncoder.encode(password);

        UserSession userSession = new UserSession();
        userSession.username = username;
        userSession.encodedPassword = encode;
        userSession.sessId = sessId;

        userSession.loginTs = System.currentTimeMillis();

        users.addSession(sessId, userSession);

        LoginResponse loginResponse = new LoginResponse();


        System.out.println("Saved session id " + sessId);

        TcHelperUser user = getOrCreateUser(username, users, random);

        byte[] userKeyCandidate = CryptUtil.hmacSha256(user.salt, (username + ":" + password));
        byte[] userKeyCandidateKcv = CryptUtil.aesKcv(userKeyCandidate);

        if (user.userKeyKcv == null) {
            //todo new registration
            user.userKeyKcv = userKeyCandidateKcv;

            users.addUser(username, user);
        } else {
            if (Arrays.equals(userKeyCandidateKcv, user.userKeyKcv)) {
                System.out.println("Yup, they're the same!");
            } else
                return loginResponse;
        }

        loginResponse.fullToken = sessId + ":" + token;

        return loginResponse;
    }

    private TcHelperUser getOrCreateUser(@FormParam("uname") String username,
                                         UserAndSessionsStorage users,
                                         SecureRandom random) {
        TcHelperUser user = users.getUser(username);
        if (user == null) {
            user = new TcHelperUser();
            user.username = username;
            user.salt = random.generateSeed(SALT_LEN);
        }

        return user;
    }

}
