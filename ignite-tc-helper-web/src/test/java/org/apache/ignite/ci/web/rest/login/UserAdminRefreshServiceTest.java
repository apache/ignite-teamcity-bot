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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.stream.Stream;
import javax.inject.Provider;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.exeption.ServiceUnavailableException;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcservice.TeamcityServiceConnection;
import org.apache.ignite.tcservice.model.user.User;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserAdminRefreshServiceTest {
    @Test
    public void staleAdminFlagIsInvalidatedAfterSuccessfulTeamcityRefresh() throws Exception {
        TcHelperUser user = user("admin", true);
        user.adminLastCheckedTs = 1L;

        IUserStorage users = mock(IUserStorage.class);
        when(users.allUsers()).thenReturn(Stream.of(user));

        User tcUser = new User();
        tcUser.username = "admin";

        TeamcityServiceConnection tcConn = mock(TeamcityServiceConnection.class);
        when(tcConn.getUserByUsername("admin")).thenReturn(tcUser);

        UserAdminRefreshService svc = service(users, tcConn);

        svc.refreshStaleAdmins();

        assertFalse(user.isAdmin());
        verify(users).putUser(eq("admin"), same(user));
    }

    @Test
    public void staleAdminFlagIsPreservedWhenTeamcityIsUnavailable() throws Exception {
        TcHelperUser user = user("admin", true);
        user.adminLastCheckedTs = 1L;

        IUserStorage users = mock(IUserStorage.class);
        when(users.allUsers()).thenReturn(Stream.of(user));

        TeamcityServiceConnection tcConn = mock(TeamcityServiceConnection.class);
        when(tcConn.getUserByUsername("admin")).thenThrow(
            new ServiceUnavailableException("Service unavailable", "http://tc", 503, -1));

        UserAdminRefreshService svc = service(users, tcConn);

        svc.refreshStaleAdmins();

        assertTrue(user.isAdmin());
        verify(users, never()).putUser(eq("admin"), same(user));
    }

    private static UserAdminRefreshService service(IUserStorage users, TeamcityServiceConnection tcConn)
        throws Exception {
        ITcBotConfig cfg = mock(ITcBotConfig.class);
        when(cfg.primaryServerCode()).thenReturn("public");
        when(cfg.botAdminGroups()).thenReturn(Collections.singleton("IGNITE_COMMITTERS"));

        ITcBotBgAuth bgAuth = mock(ITcBotBgAuth.class);
        when(bgAuth.getServerAuthorizerCreds()).thenReturn(creds());

        Provider<TeamcityServiceConnection> tcFactory = () -> tcConn;

        UserAdminRefreshService svc = new UserAdminRefreshService();

        setField(svc, "users", users);
        setField(svc, "cfg", cfg);
        setField(svc, "bgAuth", bgAuth);
        setField(svc, "tcFactory", tcFactory);

        return svc;
    }

    private static TcHelperUser user(String username, boolean admin) {
        TcHelperUser user = new TcHelperUser();
        user.username = username;
        user.setAdmin(admin);

        return user;
    }

    private static ITcBotUserCreds creds() {
        return new ITcBotUserCreds() {
            @Override public String getUser(String srvCode) {
                return "bot";
            }

            @Override public String getPassword(String srvCode) {
                return "password";
            }

            @Override public String getPrincipalId() {
                return "bot";
            }

            @Override public byte[] getUserKey() {
                return new byte[0];
            }
        };
    }

    private static void setField(Object target, String fieldName, Object val) throws Exception {
        Field field = UserAdminRefreshService.class.getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(target, val);
    }
}
