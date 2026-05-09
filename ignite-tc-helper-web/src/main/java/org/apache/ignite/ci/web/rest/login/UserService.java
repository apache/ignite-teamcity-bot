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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.tcbot.engine.cleaner.Cleaner;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.tcbot.visa.GitHubUserResolver;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcservice.login.ITcLogin;
import org.apache.ignite.tcservice.login.TcLoginResult;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.CredentialsUi;
import org.apache.ignite.ci.web.model.GitHubUserResolutionUi;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.TcHelperUserUi;
import org.apache.ignite.ci.web.model.UserMenuResult;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
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
        final ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        if (prov == null)
            return new SimpleResult("");

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        return userMenu(prov,
            appCtx.getInstance(IUserStorage.class),
            appCtx.getInstance(IssueDetector.class));
    }

    @NotNull public SimpleResult userMenu(ITcBotUserCreds prov, IUserStorage users, IssueDetector issueDetector) {
        final TcHelperUser user = users.getUser(prov.getPrincipalId());

        if (user == null)
            return new UserMenuResult("?");

        UserMenuResult res = new UserMenuResult(user.getDisplayName());

        res.authorizedState = issueDetector.isAuthorized();
        res.admin = user.isAdmin();

        if (user.isAdmin()) {
            users.allUsers()
                .filter(next -> !Objects.equals(user.username, next.username))
                .sorted(Comparator.comparing(TcHelperUser::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(next -> new UserMenuResult.User(next.username, next.getDisplayName(), next.isAdmin()))
                .forEach(res.users::add);
        }

        return res;
    }

    @POST
    @Path("authorize")
    public SimpleResult setAuthorizedState() {
        final ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        IssueDetector issueDetector = appCtx.getInstance(IssueDetector.class);
        final ITcBotBgAuth helper = appCtx.getInstance(ITcBotBgAuth.class);
        helper.setServerAuthorizerCreds(prov);

        issueDetector.startBackgroundCheck(prov);

        CtxListener.getApplicationContext(ctx).getInstance(TcBotTriggerAndSignOffService.class).startObserver();

        Cleaner cleaner = appCtx.getInstance(Cleaner.class);
        cleaner.startBackgroundClean();

        return userMenu(prov,
            appCtx.getInstance(IUserStorage.class),
            issueDetector);
    }

    @GET
    @Path("get")
    public TcHelperUserUi getUserData(@Nullable @QueryParam("login") final String loginParm) {
        final String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);

        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login);

        final TcHelperUser user = users.getUser(login);
        if (user == null)
            throw new NotFoundException("User not found: " + login);

        //todo can filter accessibliity
        final TcHelperUserUi tcHelperUserUi = new TcHelperUserUi(user,
                cfg.getTrackedBranches().branchesStream()
                        .map(ITrackedBranch::name)
                        .collect(Collectors.toList()) );

        //if principal is not null, can do only brief check of credentials.

        for (TcHelperUser.Credentials next : user.getCredentialsList()) {
            final CredentialsUi credsUi = new CredentialsUi();
            credsUi.serviceId = next.getServerId();
            credsUi.serviceLogin = next.getUsername();

            final byte[] encPass = next.getPasswordUnderUserKey();
            credsUi.servicePassword = encPass != null && encPass.length > 0 ? "*******" : "";
            credsUi.stale = next.isStale();
            credsUi.staleReason = next.getStaleReason();

            tcHelperUserUi.data.add(credsUi);
        }
        return tcHelperUserUi;
    }

    /**
     * @param loginParm Optional user login for admin view.
     * @param srvCode Optional server id to limit cached PR authors used for email matching.
     */
    @GET
    @Path("githubResolution")
    public GitHubUserResolutionUi githubResolution(@Nullable @QueryParam("login") final String loginParm,
        @Nullable @QueryParam("serverId") final String srvCode) {
        final String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login);

        final TcHelperUser user = users.getUser(login);
        if (user == null)
            throw new NotFoundException("User not found: " + login);

        return new GitHubUserResolutionUi(appCtx.getInstance(GitHubUserResolver.class).resolve(user,
            cachedPullRequestAuthors(appCtx, srvCode, ITcBotUserCreds.get(req))));
    }

    /**
     * @param appCtx Application context.
     * @param srvCode Optional server id.
     * @param prov Current user credentials.
     */
    private static Collection<GitHubUser> cachedPullRequestAuthors(TcBotApplicationContext appCtx,
        @Nullable String srvCode, ITcBotUserCreds prov) {
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);
        ITeamcityIgnitedProvider tcProv = appCtx.getInstance(ITeamcityIgnitedProvider.class);
        IGitHubConnIgnitedProvider ghProv = appCtx.getInstance(IGitHubConnIgnitedProvider.class);
        Collection<String> serverIds = Strings.isNullOrEmpty(srvCode) ? cfg.getServerIds() :
            java.util.Collections.singleton(srvCode);
        List<GitHubUser> res = new ArrayList<>();

        for (String serverId : serverIds) {
            if (!tcProv.hasAccess(serverId, prov))
                continue;

            List<PullRequest> prs = ghProv.server(serverId).getPullRequests();

            res.addAll(GitHubUserResolver.authors(prs));
        }

        return res;
    }


    @POST
    @Path("resetCredentials")
    public SimpleResult resetCredentials(@Nullable @FormParam("login") final String loginParm) {
        final String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;

        final IUserStorage users = CtxListener.getApplicationContext(ctx).getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login);

        final TcHelperUser user = users.getUser(login);
        if (user == null)
            throw new NotFoundException("User not found: " + login);

        user.resetCredentials();

        users.putUser(login, user);

        return new SimpleResult("");
    }

    @POST
    @Path("addService")
    public SimpleResult addCredentials(@FormParam("serviceId") String svcId,
                                       @FormParam("serviceLogin") String svcLogin,
                                       @FormParam("servicePassword") String svcPwd) {
        Preconditions.checkState(!Strings.isNullOrEmpty(svcId));
        Preconditions.checkState(!Strings.isNullOrEmpty(svcLogin));
        Preconditions.checkState(!Strings.isNullOrEmpty(svcPwd));

        final ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        final String currUserLogin = prov.getPrincipalId();
        final TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        final ITcLogin tcLogin = appCtx.getInstance(ITcLogin.class);

        final IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser user = users.getUser(currUserLogin);
        final TcLoginResult loginResult = tcLogin.checkServiceUserAndPasswordResult(svcId, svcLogin, svcPwd);
        final User tcAddUser = loginResult.user();

        if (tcAddUser == null)
            return new SimpleResult("Service rejected credentials/user not found");

        final TcHelperUser.Credentials creds = user.getOrCreateCreds(svcId).setLogin(svcLogin);

        creds.setPassword(svcPwd, prov.getUserKey());

        user.enrichUserData(tcAddUser);

        users.putUser(currUserLogin, user);

        return new SimpleResult("");
    }

    @POST
    @Path("saveUserData")
    public SimpleResult saveUserData(@Nullable @FormParam("login") final String loginParm,
        @Nullable @FormParam("email") final String email,
        @Nullable @FormParam("fullName") final String fullName,
        @Nullable @FormParam("githubIds") final String githubIds,
        Form form) {

        final String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        final String login = Strings.isNullOrEmpty(loginParm) ? currUserLogin : loginParm;

        final IUserStorage users = CtxListener.getApplicationContext(ctx).getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login);

        final TcHelperUser user = users.getUser(login);
        if (user == null)
            throw new NotFoundException("User not found: " + login);

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
        user.githubIds = normalizeGithubIds(githubIds);

        users.putUser(login, user);

        return new SimpleResult("");
    }

    /**
     * @param currUser Current user.
     * @param currUserLogin Current user login.
     * @param requestedLogin Requested user login.
     */
    private void ensureCanAccessUser(TcHelperUser currUser, String currUserLogin, String requestedLogin) {
        if (!Objects.equals(currUserLogin, requestedLogin) && (currUser == null || !currUser.isAdmin()))
            throw new ForbiddenException("Only bot admin can access other users");
    }

    /**
     * @param githubIds GitHub logins separated by comma, semicolon, or whitespace.
     */
    private static Set<String> normalizeGithubIds(@Nullable String githubIds) {
        Set<String> res = new LinkedHashSet<>();

        if (Strings.isNullOrEmpty(githubIds))
            return res;

        for (String raw : githubIds.split("[,;\\s]+")) {
            String trimmed = raw.trim();

            if (Strings.isNullOrEmpty(trimmed))
                continue;

            Preconditions.checkState(trimmed.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?"),
                "Invalid GitHub ID: " + trimmed);

            Preconditions.checkState(!trimmed.contains("--"), "Invalid GitHub ID: " + trimmed);

            res.add(trimmed);
        }

        return res;
    }
}
