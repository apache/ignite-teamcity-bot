package org.apache.ignite.ci.web.rest.login;

import com.google.common.base.Preconditions;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.tcmodel.user.User;
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
import java.util.Collection;
import java.util.Iterator;

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
            return doLogin(username, password, users, primaryServerId,
                    tcHelper.getServerIds());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public LoginResponse doLogin(@FormParam("uname") String username,
                                 @FormParam("psw") String password,
                                 UserAndSessionsStorage users,
                                 String primaryServerId,
                                 Collection<String> serverIds) {
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

        final User tcUser = checkService(username, password, primaryServerId);

        if (user.userKeyKcv == null) {
            if (tcUser == null) {
                loginResponse.errorMessage =
                        "Service " + primaryServerId + " rejected credentials/user not found";

                return loginResponse;
            }

            //todo new registration should be checked on server first
            user.userKeyKcv = userKeyCandidateKcv;

            user.email = tcUser.email;
            user.fullName = tcUser.name;

            user.getOrCreateCreds(primaryServerId).setLogin(username).setPassword(password, userKeyCandidate);

            for (String addSrvId : serverIds) {
                if (!addSrvId.equals(primaryServerId)) {
                    final User tcAddUser = checkService(username, password, addSrvId);

                    if (tcAddUser != null) {
                        user.getOrCreateCreds(addSrvId).setLogin(username).setPassword(password, userKeyCandidate);

                        user.enrichUserData(tcAddUser);
                    }
                }
            }

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

    protected User checkService(   String username, @FormParam("psw") String password,
        String primaryServerId) {
        return checkServiceUserAndPassword(primaryServerId, username, password);
    }

    public static User checkServiceUserAndPassword(String serverId, String username, String password) {
        try {
            try(IgniteTeamcityHelper igniteTeamcityHelper = new IgniteTeamcityHelper(serverId)) {
                igniteTeamcityHelper.setAuthData(username, password);

                final User tcUser = igniteTeamcityHelper.getUserByUsername(username);

                /*
                final List<UserRef> usersRefs = users.getUsersRefs();

                for (UserRef next : usersRefs) {
                    if (next.username.equals(username)) {
                        System.err.println("Found ");
                    }
                }*/

                if (tcUser != null)
                    System.err.println(tcUser);

                return tcUser;
            }
        } catch (ServiceUnauthorizedException e) {
            System.err.println("Service " + serverId + " rejected credentials from " + username);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
