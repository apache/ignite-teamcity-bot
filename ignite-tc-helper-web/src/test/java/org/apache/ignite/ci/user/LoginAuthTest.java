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
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcservice.login.ITcLogin;
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
    private ITcLogin tcLogin = (serverId, username, password) -> new User();

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
    public void testAuthFailedWithBrokenToken() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = createLogin();

        String fullToken = login.doLogin("user", "password", storage, "public", Collections.emptySet(), tcLogin).fullToken;

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
}
