package org.apache.ignite.ci.user;

import org.apache.ignite.ci.user.LoginResponse;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.rest.login.Login;
import org.eclipse.jetty.server.Authentication;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.container.ContainerRequestContext;

import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class LoginAuthTest {
    @Test
    public void testNewUserLogin() {
        UserAndSessionsStorage storage = mockOneSessionStor();

        Login login = new Login();

        LoginResponse login1 = login.doLogin("user", "password", storage);
        assertNotNull(login1.fullToken);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter();
        ContainerRequestContext re = Mockito.mock(ContainerRequestContext.class);

        assertTrue(authenticationFilter.authenticate(re, login1.fullToken, storage));

        assertNotNull(login.doLogin("user", "password", storage).fullToken);

        assertNull(login.doLogin("user", "assword", storage).fullToken);

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
        }).when(storage).addSession(anyString(), any(UserSession.class));


        AtomicReference<TcHelperUser> userRef = new AtomicReference<>();
        when(storage.getUser(anyString())).thenAnswer((i) -> userRef.get());
        doAnswer(i -> {
            TcHelperUser argument = i.getArgument(1);

            userRef.set(argument);

            return (Void) null;
        }).when(storage).addUser(anyString(), any(TcHelperUser.class));

        return storage;
    }
}
