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
import com.google.common.base.Strings;
import com.google.inject.Injector;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.teamcity.pure.ITcLogin;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.CredentialsUi;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.TcHelperUserUi;
import org.apache.ignite.ci.web.model.UserMenuResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Path(UserService.USER)
@Produces(MediaType.APPLICATION_JSON)
public class UserService {
    public static final String USER = "user";

    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("currentUserName")
    public SimpleResult currentUserName() {
        final ICredentialsProv prov = ICredentialsProv.get(req);
        if (prov == null)
            return new SimpleResult("");


        final ITcHelper helper = CtxListener.getTcHelper(ctx);

        return userMenu(prov, helper);
    }

    @NotNull public SimpleResult userMenu(ICredentialsProv prov, ITcHelper helper) {
        final UserAndSessionsStorage users = helper.users();
        final TcHelperUser user = users.getUser(prov.getPrincipalId());

        UserMenuResult res = new UserMenuResult(user.getDisplayName());

        res.authorizedState = helper.issueDetector().isAuthorized();

        return res;
    }

    @POST
    @Path("authorize")
    public SimpleResult setAuthorizedState() {
        final ICredentialsProv prov = ICredentialsProv.get(req);

        final ITcHelper helper = CtxListener.getTcHelper(ctx);

        helper.setServerAuthorizerCreds(prov);

        IssueDetector detector = helper.issueDetector();

        detector.startBackgroundCheck(helper, prov);

        CtxListener.getInjector(ctx).getInstance(TcBotTriggerAndSignOffService.class).startObserver();

        return userMenu(prov, helper);
    }

    @GET
    @Path("get")
    public TcHelperUserUi getUserData(@Nullable @QueryParam("login") final String loginParm) {
        final String currUserLogin = ICredentialsProv.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;
        ITcHelper helper = CtxListener.getTcHelper(ctx);
        final TcHelperUser user = helper.users().getUser(login);

        final TcHelperUserUi tcHelperUserUi = new TcHelperUserUi(user, helper.getTrackedBranchesIds());

        //if principal is not null, can do only brief check of credentials.

        for (TcHelperUser.Credentials next : user.getCredentialsList()) {
            final CredentialsUi credsUi = new CredentialsUi();
            credsUi.serviceId = next.getServerId();
            credsUi.serviceLogin = next.getUsername();

            final byte[] encPass = next.getPasswordUnderUserKey();
            credsUi.servicePassword = encPass != null && encPass.length > 0 ? "*******" : "";

            tcHelperUserUi.data.add(credsUi);
        }

        //todo if user is not current disable add creds
        return tcHelperUserUi;
    }


    @POST
    @Path("resetCredentials")
    public SimpleResult resetCredentials(@Nullable @FormParam("login") final String loginParm) {
        final String currUserLogin = ICredentialsProv.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;
        //todo check admin

        final UserAndSessionsStorage users = CtxListener.getTcHelper(ctx).users();
        final TcHelperUser user = users.getUser(login);

        user.resetCredentials();

        users.putUser(login, user);

        return new SimpleResult("");
    }

    @POST
    @Path("addService")
    public SimpleResult addCredentials(@FormParam("serviceId") String serviceId,
                                       @FormParam("serviceLogin") String serviceLogin,
                                       @FormParam("servicePassword") String servicePassword) {
        Preconditions.checkState(!Strings.isNullOrEmpty(serviceId));
        Preconditions.checkState(!Strings.isNullOrEmpty(serviceLogin));
        Preconditions.checkState(!Strings.isNullOrEmpty(servicePassword));

        final ICredentialsProv prov = ICredentialsProv.get(req);
        final String currentUserLogin = prov.getPrincipalId();
        final Injector injector = CtxListener.getInjector(ctx);
        final ITcLogin tcLogin = injector.getInstance(ITcLogin.class);

        final UserAndSessionsStorage users = CtxListener.getTcHelper(ctx).users();
        final TcHelperUser user = users.getUser(currentUserLogin);

        //todo check service credentials first
        final User tcAddUser = tcLogin.checkServiceUserAndPassword(serviceId, serviceLogin, servicePassword);

        if (tcAddUser == null)
            return new SimpleResult("Service rejected credentials/user not found");

        final TcHelperUser.Credentials credentials = new TcHelperUser.Credentials(serviceId, serviceLogin);

        credentials.setPassword(servicePassword, prov.getUserKey());

        user.enrichUserData(tcAddUser);

        user.getCredentialsList().add(credentials);

        users.putUser(currentUserLogin, user);

        return new SimpleResult("");
    }

    @POST
    @Path("saveUserData")
    public SimpleResult saveUserData(@Nullable @FormParam("login") final String loginParm,
        @Nullable @FormParam("email") final String email,
        @Nullable @FormParam("fullName") final String fullName,
        Form form) {

        final String currUserLogin = ICredentialsProv.get(req).getPrincipalId();
        final String login = currUserLogin; //todo check admin Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;

        final UserAndSessionsStorage users = CtxListener.getTcHelper(ctx).users();
        final TcHelperUser user = users.getUser(login);

        user.resetNotifications();
        form.asMap().forEach((k, v) -> {
            String notify_ = "notify_";
            if (k.startsWith(notify_) && "1".equals(v.get(0))) {
                String branch = k.substring(notify_.length());

                System.err.println("Notify enabled for " + branch);
                user.addNotification(branch);
            }
        });


        user.fullName = fullName;
        user.email = email;

        users.putUser(user.username, user);

        return new SimpleResult("");
    }

}
