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
import java.util.Locale;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path(UserService.USER)
@Produces(MediaType.APPLICATION_JSON)
public class UserService {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

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
        ITcBotConfig cfg = CtxListener.getApplicationContext(ctx).getInstance(ITcBotConfig.class);
        res.userAdmin = isUserAdmin(user, cfg);
        res.canClaimUserAdmin = !anyUserAdminExists(users, cfg);

        return res;
    }

    /**
     * @return All bot users. Contains sensitive user data, user-admin only.
     */
    @GET
    @Path("list")
    public List<UserListItem> users() {
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);
        TcHelperUser currUser = users.getUser(ITcBotUserCreds.get(req).getPrincipalId());

        ensureUserAdmin(currUser, cfg);

        Collection<GitHubUser> cachedAuthors = cachedPullRequestAuthorsOrEmpty(appCtx, null, ITcBotUserCreds.get(req));
        GitHubUserResolver gitHubUserResolver = appCtx.getInstance(GitHubUserResolver.class);

        return users.allUsers()
            .sorted(Comparator.comparing(TcHelperUser::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .map(user -> new UserListItem(user, isConfigUserAdmin(user.username, cfg),
                gitHubUserResolver.resolve(user, cachedAuthors)))
            .collect(Collectors.toList());
    }

    /**
     * Claims the initial user-admin role when no DB/config user-admin exists yet.
     */
    @POST
    @Path("claimUserAdmin")
    public UserMenuResult claimUserAdmin() {
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);
        IssueDetector issueDetector = appCtx.getInstance(IssueDetector.class);
        String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        TcHelperUser currUser = users.getUser(currUserLogin);

        if (currUser == null)
            throw new NotFoundException("User not found: " + currUserLogin);

        if (anyUserAdminExists(users, cfg))
            throw new ForbiddenException("User admin already exists");

        currUser.setUserAdmin(true);

        users.putUser(currUserLogin, currUser);

        return (UserMenuResult)userMenu(ITcBotUserCreds.get(req), users, issueDetector);
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
        ensureCanAccessUser(currUser, currUserLogin, login, appCtx.getInstance(ITcBotConfig.class));

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
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);

        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login, cfg);

        final TcHelperUser user = users.getUser(login);
        if (user == null)
            throw new NotFoundException("User not found: " + login);

        Collection<GitHubUser> cachedAuthors = cachedPullRequestAuthorsOrEmpty(appCtx, srvCode, ITcBotUserCreds.get(req));

        GitHubUserResolutionUi res = new GitHubUserResolutionUi(appCtx.getInstance(GitHubUserResolver.class)
            .resolve(user, cachedAuthors));

        res.allConfiguredLogins.addAll(configuredGithubIds(users).keySet());

        return res;
    }

    /**
     * Adds GitHub login to the current user profile.
     *
     * @param githubId GitHub login.
     * @param srvCode Optional server id to limit cached PR authors used for email matching.
     */
    @POST
    @Path("claimGithubId")
    public GitHubUserResolutionUi claimGithubId(@FormParam("githubId") String githubId,
        @Nullable @QueryParam("serverId") String srvCode) {
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        IUserStorage users = appCtx.getInstance(IUserStorage.class);
        String currUserLogin = ITcBotUserCreds.get(req).getPrincipalId();
        TcHelperUser user = users.getUser(currUserLogin);

        if (user == null)
            throw new NotFoundException("User not found: " + currUserLogin);

        Set<String> normalizedIds = normalizeGithubIds(githubId);

        Preconditions.checkState(normalizedIds.size() == 1, "One GitHub ID is expected.");

        String normalizedId = normalizedIds.iterator().next();
        boolean alreadyConfigured = user.getGithubIds().stream()
            .anyMatch(normalizedId::equalsIgnoreCase);

        Map<String, String> configuredIds = configuredGithubIds(users);
        String configuredUser = configuredIds.get(normalizedId.toLowerCase(Locale.ROOT));

        Preconditions.checkState(configuredUser == null || configuredUser.equals(currUserLogin),
            "GitHub ID is already configured for user: " + configuredUser);

        if (!alreadyConfigured) {
            user.getGithubIds().add(normalizedId);

            users.putUser(currUserLogin, user);
        }

        GitHubUserResolutionUi res = new GitHubUserResolutionUi(appCtx.getInstance(GitHubUserResolver.class)
            .resolve(user, java.util.Collections.emptyList()));

        res.allConfiguredLogins.addAll(configuredGithubIds(users).keySet());

        return res;
    }

    /**
     * @param appCtx Application context.
     * @param srvCode Optional server id.
     * @param prov Current user credentials.
     */
    private static Collection<GitHubUser> cachedPullRequestAuthorsOrEmpty(TcBotApplicationContext appCtx,
        @Nullable String srvCode, ITcBotUserCreds prov) {
        try {
            return cachedPullRequestAuthors(appCtx, srvCode, prov);
        }
        catch (RuntimeException e) {
            logger.debug("Failed to load cached PR authors for GitHub profile resolution [serverId={}]", srvCode, e);

            return java.util.Collections.emptyList();
        }
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

        final TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        final IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login, appCtx.getInstance(ITcBotConfig.class));

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
            return new SimpleResult(Login.serviceLoginErrorMessage(svcId, loginResult));

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

        final TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        final IUserStorage users = appCtx.getInstance(IUserStorage.class);
        final TcHelperUser currUser = users.getUser(currUserLogin);
        ensureCanAccessUser(currUser, currUserLogin, login, appCtx.getInstance(ITcBotConfig.class));

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
    private void ensureCanAccessUser(TcHelperUser currUser, String currUserLogin, String requestedLogin,
        ITcBotConfig cfg) {
        if (!Objects.equals(currUserLogin, requestedLogin) && !isUserAdmin(currUser, cfg))
            throw new ForbiddenException("Only user admin can access other users");
    }

    /**
     * @param currUser Current user.
     * @param cfg Config.
     */
    private void ensureUserAdmin(TcHelperUser currUser, ITcBotConfig cfg) {
        if (!isUserAdmin(currUser, cfg))
            throw new ForbiddenException("Only user admin can access users");
    }

    /**
     * @param user User.
     * @param cfg Config.
     */
    private boolean isUserAdmin(@Nullable TcHelperUser user, ITcBotConfig cfg) {
        return user != null && (user.isUserAdmin() || isConfigUserAdmin(user.username, cfg));
    }

    /**
     * @param users Users.
     * @param cfg Config.
     */
    private boolean anyUserAdminExists(IUserStorage users, ITcBotConfig cfg) {
        Collection<String> cfgUserAdmins = cfg.userAdmins();

        if (cfgUserAdmins != null && !cfgUserAdmins.isEmpty())
            return true;

        return users.allUsers().anyMatch(TcHelperUser::isUserAdmin);
    }

    /**
     * @param username Username.
     * @param cfg Config.
     */
    private boolean isConfigUserAdmin(@Nullable String username, ITcBotConfig cfg) {
        if (Strings.isNullOrEmpty(username))
            return false;

        Collection<String> cfgUserAdmins = cfg.userAdmins();

        if (cfgUserAdmins == null)
            return false;

        String normalized = username.toLowerCase(Locale.ROOT);

        return cfgUserAdmins.stream()
            .filter(Objects::nonNull)
            .map(s -> s.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
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

    /**
     * @param users User storage.
     */
    private static Map<String, String> configuredGithubIds(IUserStorage users) {
        return users.allUsers()
            .flatMap(user -> user.getGithubIds().stream()
                .filter(id -> !Strings.isNullOrEmpty(id))
                .map(id -> new java.util.AbstractMap.SimpleEntry<>(id.toLowerCase(Locale.ROOT), user.username)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first,
                java.util.LinkedHashMap::new));
    }

    /** User list row. */
    public static class UserListItem {
        /** */
        public String login;

        /** */
        public String displayName;

        /** */
        public String fullName;

        /** */
        public String email;

        /** */
        public String githubIds;

        /** */
        public String autoResolvedGithubIds;

        /** */
        public boolean admin;

        /** */
        public Long adminLastCheckedTs;

        /** */
        public boolean userAdmin;

        /** */
        public boolean configUserAdmin;

        /** */
        public Long lastLoginTs;

        /** */
        public int credentials;

        /** */
        public int staleCredentials;

        /** */
        public UserListItem(TcHelperUser user, boolean configUserAdmin, GitHubUserResolver.Resolution gitHubResolution) {
            login = user.username;
            displayName = user.getDisplayName();
            fullName = user.fullName;
            email = user.email;
            githubIds = String.join(", ", user.getGithubIds());
            autoResolvedGithubIds = String.join(", ", gitHubResolution.autoResolvedLogins);
            admin = user.isAdmin();
            adminLastCheckedTs = user.adminLastCheckedTs;
            userAdmin = user.isUserAdmin() || configUserAdmin;
            this.configUserAdmin = configUserAdmin;
            lastLoginTs = user.lastLoginTs;
            credentials = user.getCredentialsList().size();
            staleCredentials = (int)user.getCredentialsList().stream()
                .filter(TcHelperUser.Credentials::isStale)
                .count();
        }
    }
}
