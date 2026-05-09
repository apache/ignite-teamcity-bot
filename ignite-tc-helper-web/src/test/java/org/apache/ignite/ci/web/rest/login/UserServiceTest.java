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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.Form;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.tcbot.visa.GitHubUserResolver;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserServiceTest {
    @Test
    public void adminSavesRequestedUserData() throws Exception {
        TcHelperUser admin = user("admin", true);
        admin.setUserAdmin(true);
        TcHelperUser other = user("other", false);

        IUserStorage users = mock(IUserStorage.class);
        when(users.getUser("admin")).thenReturn(admin);
        when(users.getUser("other")).thenReturn(other);

        UserService svc = service(users, creds("admin"));

        Form form = new Form();
        form.param("notify_master", "1");

        svc.saveUserData("other", "other@example.org", "Other User", null, form);

        assertEquals("Admin User", admin.fullName);
        assertEquals("admin@example.org", admin.email);
        assertFalse(admin.isSubscribedToBranch("master"));

        assertEquals("Other User", other.fullName);
        assertEquals("other@example.org", other.email);
        assertTrue(other.isSubscribedToBranch("master"));

        verify(users).putUser(eq("other"), same(other));
        verify(users, never()).putUser(eq("admin"), same(admin));
    }

    @Test
    public void adminResetsRequestedUserCredentials() throws Exception {
        TcHelperUser admin = user("admin", true);
        admin.setUserAdmin(true);
        TcHelperUser other = user("other", false);
        other.getOrCreateCreds("apache").setLogin("other").setPassword("password", new byte[16]);

        IUserStorage users = mock(IUserStorage.class);
        when(users.getUser("admin")).thenReturn(admin);
        when(users.getUser("other")).thenReturn(other);

        UserService svc = service(users, creds("admin"));

        svc.resetCredentials("other");

        assertTrue(other.getCredentialsList().isEmpty());
        assertEquals("Admin User", admin.fullName);

        verify(users).putUser(eq("other"), same(other));
        verify(users, never()).putUser(eq("admin"), same(admin));
    }

    @Test(expected = ForbiddenException.class)
    public void nonAdminCannotResetOtherUserCredentials() throws Exception {
        TcHelperUser user = user("user", false);
        TcHelperUser other = user("other", false);

        IUserStorage users = mock(IUserStorage.class);
        when(users.getUser("user")).thenReturn(user);
        when(users.getUser("other")).thenReturn(other);

        service(users, creds("user")).resetCredentials("other");
    }

    @Test
    public void userPageLinksToUserManagementPage() throws IOException {
        String html = new String(Files.readAllBytes(userHtml()), StandardCharsets.UTF_8);
        String commonScript = new String(Files.readAllBytes(commonJs()), StandardCharsets.UTF_8);
        String usersPage = new String(Files.readAllBytes(usersHtml()), StandardCharsets.UTF_8);

        assertTrue(html.contains("loadUserAdminLink()"));
        assertTrue(html.contains("id=\"userAdminBlock\""));
        assertTrue(html.contains("rest/user/currentUserName"));
        assertTrue(commonScript.contains("renderUserAdminLink"));
        assertTrue(commonScript.contains("/users.html"));
        assertTrue(usersPage.contains("rest/user/list"));
        assertTrue(usersPage.contains("Claim user admin"));
        assertTrue(usersPage.contains("TC Ignite Committer"));
        assertTrue(usersPage.contains("Auto GitHub IDs"));
    }

    @Test
    public void mainMenuDoesNotRenderAdminUsersDropdown() throws IOException {
        String commonScript = new String(Files.readAllBytes(commonJs()), StandardCharsets.UTF_8);

        assertFalse(commonScript.contains("adminUsersMenu"));
        assertFalse(commonScript.contains("dropbtn'>Users"));
        assertFalse(commonScript.contains("renderAdminUsersList"));
    }

    @Test
    public void userAdminListIncludesCurrentUser() throws Exception {
        TcHelperUser admin = user("admin", true);
        admin.setUserAdmin(true);
        admin.lastLoginTs = 123L;
        TcHelperUser other = user("other", false);

        IUserStorage users = mock(IUserStorage.class);
        when(users.getUser("admin")).thenReturn(admin);
        when(users.allUsers()).thenReturn(Stream.of(admin, other));

        assertEquals(2, service(users, creds("admin")).users().size());
    }

    @Test
    public void firstUserCanClaimUserAdminWhenNoUserAdminExists() throws Exception {
        TcHelperUser user = user("user", false);

        IUserStorage users = mock(IUserStorage.class);
        when(users.getUser("user")).thenReturn(user);
        when(users.allUsers()).thenReturn(Stream.of(user)).thenReturn(Stream.of(user));

        service(users, creds("user")).claimUserAdmin();

        assertTrue(user.isUserAdmin());
        verify(users).putUser(eq("user"), same(user));
    }

    private static UserService service(IUserStorage users, ITcBotUserCreds creds) throws Exception {
        TcBotApplicationContext appCtx = mock(TcBotApplicationContext.class);
        when(appCtx.getInstance(IUserStorage.class)).thenReturn(users);
        ITcBotConfig cfg = mock(ITcBotConfig.class);
        when(cfg.userAdmins()).thenReturn(Collections.emptyList());
        when(appCtx.getInstance(ITcBotConfig.class)).thenReturn(cfg);
        IssueDetector issueDetector = mock(IssueDetector.class);
        when(appCtx.getInstance(IssueDetector.class)).thenReturn(issueDetector);
        when(appCtx.getInstance(GitHubUserResolver.class)).thenReturn(new GitHubUserResolver());

        ServletContext ctx = mock(ServletContext.class);
        when(ctx.getAttribute(anyString())).thenReturn(appCtx);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(ITcBotUserCreds._KEY)).thenReturn(creds);

        UserService svc = new UserService();

        setField(svc, "ctx", ctx);
        setField(svc, "req", req);

        return svc;
    }

    private static TcHelperUser user(String username, boolean admin) {
        TcHelperUser user = new TcHelperUser();
        user.username = username;
        user.fullName = username.substring(0, 1).toUpperCase() + username.substring(1) + " User";
        user.email = username + "@example.org";
        user.setAdmin(admin);

        return user;
    }

    private static ITcBotUserCreds creds(String principalId) {
        return new ITcBotUserCreds() {
            @Override public String getUser(String srvCode) {
                return null;
            }

            @Override public String getPassword(String srvCode) {
                return null;
            }

            @Override public String getPrincipalId() {
                return principalId;
            }

            @Override public byte[] getUserKey() {
                return new byte[0];
            }
        };
    }

    private static void setField(Object target, String fieldName, Object val) throws Exception {
        Field field = UserService.class.getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(target, val);
    }

    private static Path userHtml() {
        Path projectPath = Paths.get("src/main/webapp/user.html");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/user.html");
    }

    private static Path commonJs() {
        Path projectPath = Paths.get("src/main/webapp/js/common-1.7.js");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/js/common-1.7.js");
    }

    private static Path usersHtml() {
        Path projectPath = Paths.get("src/main/webapp/users.html");

        if (Files.exists(projectPath))
            return projectPath;

        return Paths.get("ignite-tc-helper-web/src/main/webapp/users.html");
    }
}
