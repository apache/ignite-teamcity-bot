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

package org.apache.ignite.ci.web.auth;

import com.google.common.base.Throwables;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.crypto.BadPaddingException;
import javax.servlet.ServletContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.user.UserSession;
import org.apache.ignite.ci.util.Base64Util;
import org.apache.ignite.ci.util.CryptUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;
import org.glassfish.jersey.internal.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters all Jetty request and performs authentication and authorization.
 */
@Provider
public class AuthenticationFilter implements ContainerRequestFilter {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

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

        if (!authenticate(requestContext, tokenFull, CtxListener.getTcHelper(context).users())) {
            requestContext.abortWith(rspUnathorized());

            return;
        }

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

    public boolean authenticate(ContainerRequestContext requestContext,
                                String tokenFull,
                                UserAndSessionsStorage users) {

        final StringTokenizer tokenizer = new StringTokenizer(tokenFull, ":");

        final String sessId = tokenizer.nextToken();
        final String token = tokenizer.nextToken();

        UserSession session = users.getSession(sessId);

        if (session == null) {
            logger.warn("Users session not found " + sessId + " enforcing login");

            return false;
        }

        if(requestContext.getUriInfo()!=null)
            logger.info("[[" + session.username + "]] "+ requestContext.getUriInfo().getPath() +" Session:" + sessId + "");

        TcHelperUser user = users.getUser(session.username);
        if (user == null) {
            logger.error("No such user " + session.username + " for " + sessId + " enforcing login");

            requestContext.abortWith(rspUnathorized());
            return false;
        }

         //System.out.println(user);

        if (user.userKeyKcv == null) {
            System.out.println("User not initialised " + session.username + ",failed at " + sessId + " enforcing login");

            return false;
        }


        byte[] userKey;
        try {
            userKey = CryptUtil.aesDecrypt(Base64Util.decodeString(token), session.userKeyUnderToken);
            byte[] userKeyKcv = CryptUtil.aesKcv(userKey);

            if(!Arrays.equals(userKeyKcv, user.userKeyKcv)) {
                System.out.println("User provided " + session.username + " invalid token ,failed at " + sessId + " enforcing login");

                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }

        session.lastActiveTs = System.currentTimeMillis();

        users.putSession(sessId, session);

        requestContext.setProperty(ICredentialsProv._KEY, createCredsProv(user, userKey));

        return true;
    }

    @NotNull
    private ICredentialsProv createCredsProv(TcHelperUser user, byte[] userKey) {
        return new ICredentialsProv() {
            @Override
            public String getUser(String server) {
                TcHelperUser.Credentials credentials = user.getCredentials(server);
                if (credentials == null)
                    return null;

                return credentials.getUsername();
            }

            @Override
            public String getPassword(String server) {
                TcHelperUser.Credentials credentials = user.getCredentials(server);
                if (credentials == null)
                    return null;

                byte[] encPass = credentials.getPasswordUnderUserKey();
                if (encPass == null)
                    return null;

                try {
                    byte[] bytes = CryptUtil.aesDecryptP5Pad(userKey, encPass);

                    return new String(bytes, CryptUtil.CHARSET);
                } catch (Exception e) {
                    e.printStackTrace();

                    if (Throwables.getCausalChain(e).stream().anyMatch(t -> t instanceof BadPaddingException)) {
                        throw new ServiceUnauthorizedException("Invalid credentials stored for " +
                                credentials.getUsername() + " for service [" + server + "]");
                    }

                    return null;
                }
            }

            @Override
            public String getPrincipalId() {
                return user.username;
            }

            @Override public byte[] getUserKey() {
                return userKey;
            }
        };
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
