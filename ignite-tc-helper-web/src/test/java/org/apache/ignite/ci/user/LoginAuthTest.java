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

package org.apache.ignite.ci.user;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.container.ContainerRequestContext;
import org.apache.ignite.tcbot.engine.user.UserAndSessionsStorage;
import org.apache.ignite.tcservice.model.user.GroupRef;
import org.apache.ignite.tcservice.model.user.Groups;
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcservice.login.ITcLogin;
import org.apache.ignite.tcservice.login.TcLoginResult;
import org.apache.ignite.tcbot.common.util.Base64Util;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.rest.login.Login;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class LoginAuthTest {
    private ITcLogin tcLogin = (serverId, username, password) -> "password".equals(password) ? new User() : null;

    @Test
    public void testNewUserLogin() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse login1
            = login.doLogin("user", "password", storage, "public", Collections.emptySet(), tcLogin);
        assertNotNull(login1.fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext re = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(re, login1.fullToken, storage));

        assertNotNull(login.doLogin("user", "password", storage, "public", Collections.emptySet(), tcLogin).fullToken);

        assertNull(login.doLogin("user", "assword", storage, "public", Collections.emptySet(), tcLogin).fullToken);

        System.out.println(storage.getUser("user"));
    }

    @NotNull
    private UserAndSessionsStorage mockOneSessionStor() {
        UserAndSessionsStorage storage = Mockito.mock(UserAndSessionsStorage.class);

        AtomicReference<UserSession> sessionRef = new AtomicReference<>();
        when(storage.getSession(anyString())).thenAnswer((i) -> sessionRef.get());
        doAnswer(i -> {
            UserSession argument = i.getArgument(1);
            sessionRef.set(argument);
            return (Void)null;
        }).when(storage).putSession(anyString(), any(UserSession.class));

        AtomicReference<TcHelperUser> userRef = new AtomicReference<>();
        when(storage.getUser(anyString())).thenAnswer((i) -> userRef.get());
        doAnswer(i -> {
            TcHelperUser argument = i.getArgument(1);

            userRef.set(argument);

            return (Void)null;
        }).when(storage).putUser(anyString(), any(TcHelperUser.class));

        return storage;
    }

    private ContainerRequestContext mockCtxWithParams() {
        ContainerRequestContext ctx = Mockito.mock(ContainerRequestContext.class);
        Map<Object, Object> atts = new HashMap<>();

        when(ctx.getProperty(anyString())).thenAnswer((i) -> atts.get(i.getArgument(0)));

        doAnswer(i -> {
            atts.put(i.getArgument(0), i.getArgument(1));
            return (Void)null;
        }).when(ctx).setProperty(anyString(), any(Object.class));

        return ctx;
    }

    @Test
    public void testAdminFlagLoadedFromTeamcityGroups() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        ITcLogin adminTcLogin = (serverId, username, password) -> {
            User user = new User();
            user.username = username;
            user.setGroups(new Groups(new GroupRef("IGNITE_COMMITER", "Ignite Tests Admins")));

            return user;
        };

        LoginResponse loginResponse = login.doLogin("admin", "password", storage, "public", Collections.emptySet(),
            adminTcLogin, Collections.singleton("IGNITE_COMMITER"));

        assertNotNull(loginResponse.fullToken);
        assertTrue(storage.getUser("admin").isAdmin());
        assertNotNull(storage.getUser("admin").adminLastCheckedTs);
    }

    @Test
    public void testAdminFlagIsFalseWhenTeamcityGroupIsMissing() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        ITcLogin regularTcLogin = (serverId, username, password) -> {
            User user = new User();
            user.username = username;
            user.setGroups(new Groups(new GroupRef("OTHER_GROUP", "Other Group")));

            return user;
        };

        LoginResponse loginResponse = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            regularTcLogin, Collections.singleton("IGNITE_COMMITER"));

        assertNotNull(loginResponse.fullToken);
        assertFalse(storage.getUser("user").isAdmin());
    }

    @Test
    public void testAdminGroupMatchingDoesNotUseDisplayName() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        ITcLogin adminTcLogin = (serverId, username, password) -> {
            User user = new User();
            user.username = username;
            user.setGroups(new Groups(new GroupRef("IGNITE_COMMITER", "Ignite Tests Admins")));

            return user;
        };

        LoginResponse loginResponse = login.doLogin("admin", "password", storage, "public", Collections.emptySet(),
            adminTcLogin, Collections.singleton("Ignite Tests Admins"));

        assertNotNull(loginResponse.fullToken);
        assertFalse(storage.getUser("admin").isAdmin());
    }

    @Test
    public void testAdminFlagIsPreservedWhenTeamcityIsUnavailable() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        ITcLogin adminTcLogin = (serverId, username, password) -> {
            User user = new User();
            user.username = username;
            user.setGroups(new Groups(new GroupRef("IGNITE_COMMITER", "Ignite Tests Admins")));

            return user;
        };

        LoginResponse loginResponse = login.doLogin("admin", "password", storage, "public", Collections.emptySet(),
            adminTcLogin, Collections.singleton("IGNITE_COMMITER"));

        assertNotNull(loginResponse.fullToken);
        assertTrue(storage.getUser("admin").isAdmin());

        ITcLogin unavailableTcLogin = (serverId, username, password) -> null;

        loginResponse = login.doLogin("admin", "password", storage, "public", Collections.emptySet(),
            unavailableTcLogin, Collections.singleton("IGNITE_COMMITER"));

        assertNotNull(loginResponse.fullToken);
        assertTrue(storage.getUser("admin").isAdmin());
    }

    @Test
    public void testUserCredentials() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        String srvId = "public";
        String user = "user";
        String password = "password";
        LoginResponse loginResponse = login.doLogin(user, password, storage, srvId, Collections.emptySet(), tcLogin);
        assertNotNull(loginResponse.fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext ctx = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(ctx, loginResponse.fullToken, storage));

        ITcBotUserCreds creds = (ITcBotUserCreds)ctx.getProperty(ITcBotUserCreds._KEY);

        assertNotNull(creds);

        assertTrue(creds.hasAccess(srvId));

        assertEquals(user, creds.getUser(srvId));

        assertEquals(password, creds.getPassword(srvId));
    }

    @Test
    public void testChangedTeamcityPasswordReplacesStoredCredentials() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse loginResponse = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin);

        assertNotNull(loginResponse.fullToken);

        ITcLogin changedPasswordLogin = (serverId, username, password) -> "new-password".equals(password)
            ? new User()
            : null;

        loginResponse = login.doLogin("user", "new-password", storage, "public", Collections.emptySet(),
            changedPasswordLogin);

        assertNotNull(loginResponse.fullToken);
        assertTrue(storage.getUser("user").getCredentialsList().stream().anyMatch(TcHelperUser.Credentials::isStale));

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext ctx = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(ctx, loginResponse.fullToken, storage));

        ITcBotUserCreds creds = (ITcBotUserCreds)ctx.getProperty(ITcBotUserCreds._KEY);

        assertEquals("new-password", creds.getPassword("public"));
    }

    @Test
    public void testNewPasswordRejectedByTeamcityKeepsStoredCredentialsActive() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse initialLogin = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin);

        assertNotNull(initialLogin.fullToken);

        LoginResponse failedLogin = login.doLogin("user", "mistyped-password", storage, "public",
            Collections.emptySet(), tcLoginWithFallback(TcLoginResult.unauthorized()));

        assertNull(failedLogin.fullToken);
        assertNotNull(failedLogin.errorMessage);
        assertTrue(storage.getUser("user").getCredentialsList().stream()
            .noneMatch(TcHelperUser.Credentials::isStale));
        assertEquals("password", credentialPassword(storage, initialLogin.fullToken, "public"));
    }

    @Test
    public void testNewPasswordNotCheckedKeepsStoredCredentialsActive() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse initialLogin = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin);

        assertNotNull(initialLogin.fullToken);

        LoginResponse failedLogin = login.doLogin("user", "possible-new-password", storage, "public",
            Collections.emptySet(), tcLoginWithFallback(TcLoginResult.notChecked()));

        assertNull(failedLogin.fullToken);
        assertEquals("Password does not match stored bot credentials", failedLogin.errorMessage);
        assertTrue(storage.getUser("user").getCredentialsList().stream()
            .noneMatch(TcHelperUser.Credentials::isStale));
        assertEquals("password", credentialPassword(storage, initialLogin.fullToken, "public"));
    }

    @Test
    public void testStoredPasswordAllowsLoginWhenTeamcityIsNotChecked() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse initialLogin = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin);

        assertNotNull(initialLogin.fullToken);

        LoginResponse offlineLogin = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLoginWithFallback(TcLoginResult.notChecked()));

        assertNotNull(offlineLogin.fullToken);
        assertTrue(storage.getUser("user").getCredentialsList().stream()
            .noneMatch(TcHelperUser.Credentials::isStale));
        assertEquals("password", credentialPassword(storage, offlineLogin.fullToken, "public"));
    }

    @Test
    public void testPasswordRotationStalesServiceWithoutAcceptedNewCredentials() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse initialLogin = login.doLogin("user", "password", storage, "public",
            Collections.singleton("aux"), tcLoginAccepting(
                "public", "password",
                "aux", "password"
            ));

        assertNotNull(initialLogin.fullToken);
        assertEquals("password", credentialPassword(storage, initialLogin.fullToken, "public"));
        assertEquals("password", credentialPassword(storage, initialLogin.fullToken, "aux"));

        LoginResponse rotatedLogin = login.doLogin("user", "new-password", storage, "public",
            Collections.singleton("aux"), tcLoginAcceptingWithFallback(TcLoginResult.unauthorized(),
                "public", "new-password"
            ));

        assertNotNull(rotatedLogin.fullToken);
        assertEquals("new-password", credentialPassword(storage, rotatedLogin.fullToken, "public"));
        assertFalse(credentials(storage, rotatedLogin.fullToken).hasAccess("aux"));
        assertNull(storage.getUser("user").getCredentials("aux"));
    }

    @Test
    public void testPasswordRotationUpdatesAdditionalServiceWhenNewCredentialsAccepted() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse initialLogin = login.doLogin("user", "password", storage, "public",
            Collections.singleton("aux"), tcLoginAccepting(
                "public", "password",
                "aux", "password"
            ));

        assertNotNull(initialLogin.fullToken);

        LoginResponse rotatedLogin = login.doLogin("user", "new-password", storage, "public",
            Collections.singleton("aux"), tcLoginAccepting(
                "public", "new-password",
                "aux", "new-password"
            ));

        assertNotNull(rotatedLogin.fullToken);
        assertEquals("new-password", credentialPassword(storage, rotatedLogin.fullToken, "public"));
        assertEquals("new-password", credentialPassword(storage, rotatedLogin.fullToken, "aux"));
        assertTrue(storage.getUser("user").getCredentialsList().stream()
            .anyMatch(TcHelperUser.Credentials::isStale));
    }

    @Test
    public void testNewUserRejectedByTeamcityIsNotSaved() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        LoginResponse failedLogin = createLogin().doLogin("user", "password", storage, "public",
            Collections.emptySet(), tcLoginWithFallback(TcLoginResult.unauthorized()));

        assertNull(failedLogin.fullToken);
        assertNotNull(failedLogin.errorMessage);
        assertNull(storage.getUser("user"));
    }

    @Test
    public void testOldLocalPasswordRejectedByTeamcityKeepsCredentialsActive() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        LoginResponse loginResponse = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin);

        assertNotNull(loginResponse.fullToken);

        ITcLogin unauthorizedLogin = new ITcLogin() {
            @Override public User checkServiceUserAndPassword(String srvId, String username, String pwd) {
                return null;
            }

            @Override public TcLoginResult checkServiceUserAndPasswordResult(String srvId, String username,
                String pwd) {
                return TcLoginResult.unauthorized();
            }
        };

        loginResponse = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            unauthorizedLogin);

        assertNull(loginResponse.fullToken);
        assertNotNull(loginResponse.errorMessage);
        assertNotNull(storage.getUser("user").getCredentials("public"));
        assertTrue(storage.getUser("user").getCredentialsList().stream()
            .noneMatch(TcHelperUser.Credentials::isStale));
    }

    @Test
    public void testAuthFailedWithBrokenToken() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        String fullToken = login.doLogin("user", "password", storage, "public", Collections.emptySet(),
            tcLogin).fullToken;

        int sepIdx = fullToken.indexOf(':');
        String brokenToken = fullToken.substring(0, sepIdx + 1) +
            Base64Util.encodeBytesToString(new byte[128 / 8]);
        assertNotNull(fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext ctx = mockCtxWithParams();
        System.out.println(storage.getUser("user"));

        assertFalse(authenticationFilter.authenticate(ctx, brokenToken, storage));
    }

    @NotNull public Login createLogin() {
        return new Login();
    }

    private static ITcLogin tcLoginWithFallback(TcLoginResult fallback) {
        return tcLoginAcceptingWithFallback(fallback);
    }

    private static ITcLogin tcLoginAccepting(String... acceptedServerPasswordPairs) {
        return tcLoginAcceptingWithFallback(TcLoginResult.notChecked(), acceptedServerPasswordPairs);
    }

    private static ITcLogin tcLoginAcceptingWithFallback(TcLoginResult fallback,
        String... acceptedServerPasswordPairs) {
        Map<String, Boolean> accepted = new HashMap<>();

        for (int i = 0; i < acceptedServerPasswordPairs.length; i += 2)
            accepted.put(loginKey(acceptedServerPasswordPairs[i], acceptedServerPasswordPairs[i + 1]), true);

        return new ITcLogin() {
            @Override public User checkServiceUserAndPassword(String srvId, String username, String pwd) {
                return checkServiceUserAndPasswordResult(srvId, username, pwd).user();
            }

            @Override public TcLoginResult checkServiceUserAndPasswordResult(String srvId, String username,
                String pwd) {
                if (accepted.containsKey(loginKey(srvId, pwd))) {
                    User user = new User();
                    user.username = username;

                    return TcLoginResult.accepted(user);
                }

                return fallback;
            }
        };
    }

    private static String loginKey(String srvId, String pwd) {
        return srvId + ":" + pwd;
    }

    private ITcBotUserCreds credentials(UserAndSessionsStorage storage, String fullToken) {
        AuthenticationFilter authenticationFilter = new AuthenticationFilter();
        ContainerRequestContext ctx = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(ctx, fullToken, storage));

        return (ITcBotUserCreds)ctx.getProperty(ITcBotUserCreds._KEY);
    }

    private String credentialPassword(UserAndSessionsStorage storage, String fullToken, String srvId) {
        return credentials(storage, fullToken).getPassword(srvId);
    }
}
