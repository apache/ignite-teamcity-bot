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
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.user.UserSession;
import org.apache.ignite.ci.util.Base64Util;
import org.apache.ignite.ci.util.CryptUtil;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.lang.reflect.Method;
import java.util.*;

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

    @Override public void filter(ContainerRequestContext reqCtx) {
        Method mtd = resourceInfo.getResourceMethod();

        //todo uncomment for development
        //if(method!=null)
        //    return;

        //Access allowed for all
        if (mtd.isAnnotationPresent(PermitAll.class))
            return;

        //Access denied for all
        if (mtd.isAnnotationPresent(DenyAll.class)) {
            reqCtx.abortWith(rspForbidden());

            return;
        }

        //Get request headers
        final MultivaluedMap<String, String> headers = reqCtx.getHeaders();

        //Fetch authorization header
        final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);

        //If no authorization information present; block access
        if (authorization == null || authorization.isEmpty()) {
            reqCtx.abortWith(rspUnathorized());

            return;
        }

        //Get encoded username and encodedPassword
        String authStr = authorization.get(0);
        if(!authStr.startsWith(TOKEN_SCHEME)) {
            reqCtx.abortWith(rspForbidden());

            return;
        }

        String tokFull = authStr.substring(TOKEN_SCHEME.length()).trim();

        final UserAndSessionsStorage users = CtxListener.getTcHelper(context).users();

        try {
            users.getIgnite();
        } catch (Exception e) {
            ExceptionUtil.throwIfRest(e);

            reqCtx.abortWith(rspUnathorized());
        }

        if (!authenticate(reqCtx, tokFull, users)) {
            reqCtx.abortWith(rspUnathorized());

            return;
        }

        //Verify user access
        if (mtd.isAnnotationPresent(RolesAllowed.class)) {
            RolesAllowed rolesAnnotation = mtd.getAnnotation(RolesAllowed.class);
            Set<String> rolesSet = new HashSet<String>(Arrays.asList(rolesAnnotation.value()));

            //Is user valid?
            if (!isUserAllowed("", "", rolesSet)) {
                reqCtx.abortWith(rspForbidden());

                return;
            }
        }
    }

    public boolean authenticate(ContainerRequestContext reqCtx,
                                String tokFull,
                                UserAndSessionsStorage users) {

        final StringTokenizer tokenizer = new StringTokenizer(tokFull, ":");

        final String sessId = tokenizer.nextToken();
        final String tok = tokenizer.nextToken();

        UserSession ses = users.getSession(sessId);

        if (ses == null) {
            logger.warn("Users session not found " + sessId + " enforcing login");

            return false;
        }

        if(reqCtx.getUriInfo()!=null)
            logger.info("[[" + ses.username + "]] "+ reqCtx.getUriInfo().getPath() +" Session:" + sessId + "");

        TcHelperUser user = users.getUser(ses.username);
        if (user == null) {
            logger.error("No such user " + ses.username + " for " + sessId + " enforcing login");

            reqCtx.abortWith(rspUnathorized());
            return false;
        }

        if (user.userKeyKcv == null) {
            logger.error("User not initialised " + ses.username + ",failed at " + sessId + " enforcing login");

            return false;
        }


        byte[] userKey;
        try {
            userKey = CryptUtil.aesDecrypt(Base64Util.decodeString(tok), ses.userKeyUnderToken);
            byte[] userKeyKcv = CryptUtil.aesKcv(userKey);

            if(!Arrays.equals(userKeyKcv, user.userKeyKcv)) {
                logger.error("User provided " + ses.username + " invalid token ,failed at " + sessId + " enforcing login");

                return false;
            }

        } catch (Exception e) {
            logger.info("Exception during decrypt " + e.getMessage(), e);

            return false;
        }

        ses.lastActiveTs = System.currentTimeMillis();

        users.putSession(sessId, ses);

        reqCtx.setProperty(ICredentialsProv._KEY, createCredsProv(user, userKey));

        return true;
    }

    @NotNull
    private ICredentialsProv createCredsProv(TcHelperUser user, byte[] userKey) {
        return new ICredentialsProv() {
            @Override public String getUser(String srv) {
                TcHelperUser.Credentials creds = user.getCredentials(srv);
                if (creds == null)
                    return null;

                return creds.getUsername();
            }

            @Override public String getPassword(String srv) {
                TcHelperUser.Credentials creds = user.getCredentials(srv);
                if (creds == null)
                    return null;

                byte[] encPass = creds.getPasswordUnderUserKey();
                if (encPass == null)
                    return null;

                try {
                    byte[] bytes = CryptUtil.aesDecryptP5Pad(userKey, encPass);

                    return new String(bytes, CryptUtil.CHARSET);
                } catch (Exception e) {
                    e.printStackTrace();

                    if (Throwables.getCausalChain(e).stream().anyMatch(t -> t instanceof BadPaddingException)) {
                        throw new ServiceUnauthorizedException("Invalid credentials stored for " +
                                creds.getUsername() + " for service [" + srv + "]");
                    }

                    return null;
                }
            }

            @Override public String getPrincipalId() {
                return user.username;
            }

            @Override public byte[] getUserKey() {
                return userKey;
            }
        };
    }

    private boolean isUserAllowed(final String username, final String pwd, final Set<String> rolesSet) {
        boolean isAllowed = false;

        //Step 1. Fetch encodedPassword from database and match with encodedPassword in argument
        //If both match then get the defined role for user from database and continue; else return isAllowed [false]
        //Access the database and do this part yourself
        //String userRole = userMgr.getUserRole(username);

        if (username.equals("howtodoinjava") && pwd.equals("encodedPassword")) {
            String userRole = "ADMIN";

            //Step 2. Verify user role
            if (rolesSet.contains(userRole))
                isAllowed = true;
        }
        return isAllowed;
    }
}
