package org.apache.ignite.ci.user;

import org.apache.ignite.ci.util.Base64Util;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.rest.login.Login;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.container.ContainerRequestContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class LoginAuthTest {
    @Test
    public void testNewUserLogin() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = new Login();

        LoginResponse login1 = login.doLogin("user", "password", storage, "public");
        assertNotNull(login1.fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext re = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(re, login1.fullToken, storage));

        assertNotNull(login.doLogin("user", "password", storage, "public").fullToken);

        assertNull(login.doLogin("user", "assword", storage, "public").fullToken);

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
            return (Void) null;
        }).when(storage).putSession(anyString(), any(UserSession.class));


        AtomicReference<TcHelperUser> userRef = new AtomicReference<>();
        when(storage.getUser(anyString())).thenAnswer((i) -> userRef.get());
        doAnswer(i -> {
            TcHelperUser argument = i.getArgument(1);

            userRef.set(argument);

            return (Void) null;
        }).when(storage).putUser(anyString(), any(TcHelperUser.class));

        return storage;
    }


    private ContainerRequestContext mockCtxWithParams() {
        ContainerRequestContext ctx = Mockito.mock(ContainerRequestContext.class);
        Map<Object, Object> atts = new HashMap<>();

        when(ctx.getProperty(anyString())).thenAnswer((i) -> atts.get(i.getArgument(0)));

        doAnswer(i -> {
            atts.put(i.getArgument(0), i.getArgument(1));
            return (Void) null;
        }).when(ctx).setProperty(anyString(), any(Object.class));

        return ctx;
    }

    @Test
    public void testUserCredentials() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = new Login();

        String srvId = "public";
        String user = "user";
        String password = "password";
        LoginResponse loginResponse = login.doLogin(user, password, storage, srvId);
        assertNotNull(loginResponse.fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext ctx = mockCtxWithParams();

        assertTrue(authenticationFilter.authenticate(ctx, loginResponse.fullToken, storage));

        ICredentialsProv creds = (ICredentialsProv) ctx.getProperty(ICredentialsProv._KEY);

        assertNotNull(creds);

        assertTrue(creds.hasAccess(srvId));

        assertEquals(user, creds.getUser(srvId));

        assertEquals(password, creds.getPassword(srvId));
    }


    @Test
    public void testAuthFailedWithBrokenToken() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = new Login();

        String fullToken = login.doLogin("user", "password", storage, "public").fullToken;

        int sepIdx = fullToken.indexOf(":");
        String brokenToken = fullToken.substring(0, sepIdx + 1) +
                Base64Util.encodeBytesToString(new byte[128/8]);
        assertNotNull(fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();

        ContainerRequestContext ctx = mockCtxWithParams();
        System.out.println(storage.getUser("user"));

        assertFalse(authenticationFilter.authenticate(ctx, brokenToken, storage));
    }
}
