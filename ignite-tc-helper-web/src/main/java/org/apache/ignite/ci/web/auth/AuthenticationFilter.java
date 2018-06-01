package org.apache.ignite.ci.web.auth;

import org.apache.ignite.Ignite;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.apache.ignite.ci.user.UserSession;
import org.apache.ignite.ci.web.CtxListener;
import org.glassfish.jersey.internal.util.Base64;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.*;

import static org.apache.ignite.ci.web.rest.login.Login.USER_SESSIONS;


@Provider
public class AuthenticationFilter implements ContainerRequestFilter {
    @Context
    private ResourceInfo resourceInfo;

    @Context
    private ServletContext context;

    private static final String AUTHORIZATION_PROPERTY = "Authorization";
    private static final String AUTHENTICATION_SCHEME = "Basic";
    private static final String TOKEN_SCHEME = "Token";

    private static Response rspUnathorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("You cannot access this resource. Please (re)login").build();
    }

    private static Response rspForbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity("Access blocked for this resource").build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Method method = resourceInfo.getResourceMethod();

        //todo uncomment for development
        //if(method!=null)
        //    return;

        //Access allowed for all
        if (method.isAnnotationPresent(PermitAll.class))
            return;

        //Access denied for all
        if (method.isAnnotationPresent(DenyAll.class)) {
            requestContext.abortWith(rspForbidden());

            return;
        }

        //Get request headers
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();

        //Fetch authorization header
        final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);

        //If no authorization information present; block access
        if (authorization == null || authorization.isEmpty()) {
            requestContext.abortWith(rspUnathorized());

            return;
        }

        //Get encoded username and encodedPassword
        String authString = authorization.get(0);
        if(!authString.startsWith(TOKEN_SCHEME)) {
            requestContext.abortWith(rspForbidden());

            return;
        }

        String tokenFull = authString.substring(TOKEN_SCHEME.length()).trim();

        final StringTokenizer tokenizer = new StringTokenizer(tokenFull, ":");

        final String sessId = tokenizer.nextToken();
        final String token = tokenizer.nextToken();
        System.out.println("Session:"+sessId);
        System.out.println("token:"+token);

        Ignite ignite = CtxListener.getIgnite(context);

        UserSession session = ignite.<String, UserSession>getOrCreateCache(USER_SESSIONS).get(sessId);

        if (session == null) {
            System.out.println("Users session not found " + sessId);
            requestContext.abortWith(rspUnathorized());

            return;
        }

        String decode = null;
        try {
            decode = PasswordEncoder.decode(session.encodedPassword);
        } catch (Exception e) {
            System.out.println("Password decoding problems with session" + sessId);
            requestContext.abortWith(rspUnathorized());

            return;
        }

        // System.out.println(decode);
        System.out.println("username:"+session.username);

        requestContext.setProperty("principal", session.username);
        requestContext.setProperty("password", decode);

        //Verify user access
        if (method.isAnnotationPresent(RolesAllowed.class)) {
            RolesAllowed rolesAnnotation = method.getAnnotation(RolesAllowed.class);
            Set<String> rolesSet = new HashSet<String>(Arrays.asList(rolesAnnotation.value()));

            //Is user valid?
            if (!isUserAllowed("", "", rolesSet)) {
                requestContext.abortWith(rspForbidden());

                return;
            }
        }
    }

    private String basicAuth(String authString) {
        final String encodedUserPassword = authString.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

        //Decode username and encodedPassword
        String usernameAndPassword = new String(Base64.decode(encodedUserPassword.getBytes()));

        //Split username and encodedPassword tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");

        final String username = tokenizer.nextToken();
        final String password = tokenizer.nextToken();

        //Verifying Username and encodedPassword
        System.out.println(username);
        System.out.println(password);
        return password;
    }

    private boolean isUserAllowed(final String username, final String password, final Set<String> rolesSet) {
        boolean isAllowed = false;

        //Step 1. Fetch encodedPassword from database and match with encodedPassword in argument
        //If both match then get the defined role for user from database and continue; else return isAllowed [false]
        //Access the database and do this part yourself
        //String userRole = userMgr.getUserRole(username);

        if (username.equals("howtodoinjava") && password.equals("encodedPassword")) {
            String userRole = "ADMIN";

            //Step 2. Verify user role
            if (rolesSet.contains(userRole)) {
                isAllowed = true;
            }
        }
        return isAllowed;
    }
}
