package org.apache.ignite.ci.web.rest.login;

import com.google.common.base.Preconditions;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.util.Base64Util;
import org.apache.ignite.ci.util.CryptUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.user.LoginResponse;
import org.apache.ignite.ci.user.UserSession;
import org.apache.ignite.ci.web.model.ServerDataResponse;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.security.SecureRandom;
import java.util.Arrays;

@Path("login")
@Produces("application/json")
public class Login {
    public static final int TOKEN_LEN = 128/8;
    public static final int SESS_ID_LEN = 8;
    public static final int SALT_LEN = 16;

    @Context
    private ServletContext context;

    @GET
    @Path("primaryServerData")
    @PermitAll
    public ServerDataResponse primaryServerUrl() {
        ITcHelper tcHelper = CtxListener.getTcHelper(context);
        String serverId = tcHelper.primaryServerId();
        IAnalyticsEnabledTeamcity server = tcHelper.server(serverId, null);
        return new ServerDataResponse(server.host());
    }

    @POST
    @Path("login")
    @PermitAll
    public LoginResponse login(@FormParam("uname") String username,
                               @FormParam("psw") String password) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);

        ITcHelper tcHelper = CtxListener.getTcHelper(context);

        UserAndSessionsStorage users = tcHelper.users();

        String primaryServerId = tcHelper.primaryServerId();

        try {
            return doLogin(username, password, users, primaryServerId);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public LoginResponse doLogin(@FormParam("uname") String username,
                                 @FormParam("psw") String password,
                                 UserAndSessionsStorage users,
                                 String primaryServerId) {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = random.generateSeed(TOKEN_LEN);
        String token = Base64Util.encodeBytesToString(tokenBytes);

        String sessId = Base64Util.encodeBytesToString(random.generateSeed(SESS_ID_LEN));

        UserSession userSession = new UserSession();
        userSession.username = username;
        userSession.sessId = sessId;

        userSession.loginTs = System.currentTimeMillis();

        LoginResponse loginResponse = new LoginResponse();

        System.out.println("Saved session id " + sessId);

        TcHelperUser user = getOrCreateUser(username, users, random);

        byte[] userKeyCandidate = CryptUtil.hmacSha256(user.salt, (username + ":" + password));
        byte[] userKeyCandidateKcv = CryptUtil.aesKcv(userKeyCandidate);

        if (user.userKeyKcv == null) {
            //todo new registration should be checked on server first
            user.userKeyKcv = userKeyCandidateKcv;

            TcHelperUser.Credentials creds = user.getOrCreateCreds(primaryServerId);

            creds.setPassword(password, userKeyCandidate);

            users.putUser(username, user);
        } else {
            if (Arrays.equals(userKeyCandidateKcv, user.userKeyKcv)) {
                System.out.println("Yup, they're the same!");
            } else
                return loginResponse; //password validation failed
        }

        userSession.userKeyUnderToken = CryptUtil.aesEncrypt(tokenBytes, userKeyCandidate);

        users.putSession(sessId, userSession);

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
        } else {
            if (user.isOutdatedEntityVersion()) {
                user.userKeyKcv = null;
                user._version = TcHelperUser.LATEST_VERSION;
            }
        }

        if (user.salt == null) {
            user.salt = random.generateSeed(SALT_LEN);
        }

        return user;
    }

}
