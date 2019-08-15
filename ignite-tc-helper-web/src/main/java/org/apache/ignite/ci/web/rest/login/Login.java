/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.web.rest.login;

import com.google.common.base.Preconditions;
import com.google.inject.Injector;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcservice.login.ITcLogin;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.util.Base64Util;
import org.apache.ignite.tcbot.common.util.CryptUtil;
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

@Path("login")
@Produces("application/json")
public class Login {
    public static final int TOKEN_LEN = 128/8;
    public static final int SESS_ID_LEN = 8;
    public static final int SALT_LEN = 16;

    /** Context. */
    @Context
    private ServletContext ctx;

    @GET
    @Path("primaryServerData")
    @PermitAll
    public ServerDataResponse primaryServerUrl() {
        Injector injector = CtxListener.getInjector(ctx);

        ITcBotConfig tcBotCfg = injector.getInstance(ITcBotConfig.class);
        String srvId = tcBotCfg.primaryServerCode();
        String host = injector.getInstance(ITeamcityIgnitedProvider.class).server(srvId, null).host();
        return new ServerDataResponse(host);
    }

    @POST
    @Path("login")
    @PermitAll
    public LoginResponse login(@FormParam("uname") String username,
                               @FormParam("psw") String pwd) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(pwd);

        final Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        final ITcLogin tcLogin = injector.getInstance(ITcLogin.class);
        IUserStorage users = injector.getInstance(IUserStorage.class);

        String primarySrvCode = cfg.primaryServerCode();

        try {
            return doLogin(username, pwd, users, primarySrvCode, cfg.getServerIds(), tcLogin);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public LoginResponse doLogin(@FormParam("uname") String username,
                                 @FormParam("psw") String pwd,
        IUserStorage users,
                                 String primarySrvId,
                                 Collection<String> srvIds,
                                 ITcLogin tcLogin) {
        SecureRandom random = new SecureRandom();
        byte[] tokBytes = random.generateSeed(TOKEN_LEN);
        String tok = Base64Util.encodeBytesToString(tokBytes);

        String sessId = Base64Util.encodeBytesToString(random.generateSeed(SESS_ID_LEN));

        UserSession userSes = new UserSession();
        userSes.username = username;
        userSes.sessId = sessId;

        userSes.loginTs = System.currentTimeMillis();

        LoginResponse loginRes = new LoginResponse();

        System.out.println("Saved session id " + sessId);

        TcHelperUser user = getOrCreateUser(username, users, random);

        byte[] userKeyCandidate = CryptUtil.hmacSha256(user.salt, (username + ":" + pwd));
        byte[] userKeyCandidateKcv = CryptUtil.aesKcv(userKeyCandidate);


        final User tcUser = tcLogin.checkServiceUserAndPassword(primarySrvId, username, pwd);

        if (user.userKeyKcv == null) {
            if (tcUser == null) {
                loginRes.errorMessage =
                        "Service " + primarySrvId + " rejected credentials/user not found";

                return loginRes;
            }

            //todo new registration should be checked on server first
            user.userKeyKcv = userKeyCandidateKcv;

            user.email = tcUser.email;
            user.fullName = tcUser.name;

            user.getOrCreateCreds(primarySrvId).setLogin(username).setPassword(pwd, userKeyCandidate);

            user.enrichUserData(tcUser);

            for (String addSrvId : srvIds) {
                if (!addSrvId.equals(primarySrvId)) {
                    final User tcAddUser = tcLogin.checkServiceUserAndPassword(addSrvId, username, pwd);

                    if (tcAddUser != null) {
                        user.getOrCreateCreds(addSrvId).setLogin(username).setPassword(pwd, userKeyCandidate);

                        user.enrichUserData(tcAddUser);
                    }
                }
            }

            users.putUser(username, user);
        } else {
            if (!Arrays.equals(userKeyCandidateKcv, user.userKeyKcv))
                return loginRes; //password validation failed
        }

        //todo may be enrich user data here as well.
        userSes.userKeyUnderToken = CryptUtil.aesEncrypt(tokBytes, userKeyCandidate);

        users.putSession(sessId, userSes);

        loginRes.fullToken = sessId + ":" + tok;

        return loginRes;
    }

    private TcHelperUser getOrCreateUser(@FormParam("uname") String username,
        IUserStorage users,
        SecureRandom random) {
        TcHelperUser user = users.getUser(username);

        if (user == null) {
            user = new TcHelperUser();

            user.username = username;
        }
        else {
            if (user.isOutdatedEntityVersion()) {
                user.userKeyKcv = null;
                user._version = TcHelperUser.LATEST_VERSION;
            }
        }

        if (user.salt == null)
            user.salt = random.generateSeed(SALT_LEN);

        return user;
    }

}
