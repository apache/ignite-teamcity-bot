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
package org.apache.ignite.ci.teamcity;

import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgniteTeamcityConnection;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

public class TcLoginImpl implements ITcLogin {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcLoginImpl.class);

    @Inject
    private Provider<ITeamcity> tcFactory;

    public  User checkServiceUserAndPassword(String serverId, String username, String password) {
        try {
            ITeamcity tcConn = tcFactory.get();
            tcConn.init(serverId);

            tcConn.setAuthData(username, password);

            final User tcUser = tcConn.getUserByUsername(username);

                /*
                final List<UserRef> usersRefs = users.getUsersRefs();

                for (UserRef next : usersRefs) {
                    if (next.username.equals(username)) {
                        System.err.println("Found ");
                    }
                }*/

            if (tcUser != null)
                logger.info("TC user returned: " + tcUser);

            return tcUser;
        } catch (ServiceUnauthorizedException e) {
            final String msg = "Service " + serverId + " rejected credentials from " + username;
            System.err.println(msg);

            logger.warn(msg, e);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Unexpected login exception", e);

            return null;
        }
    }
}
