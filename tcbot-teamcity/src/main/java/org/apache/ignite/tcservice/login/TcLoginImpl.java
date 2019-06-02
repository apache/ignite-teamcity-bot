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
package org.apache.ignite.tcservice.login;

import org.apache.ignite.tcservice.TeamcityServiceConnection;
import org.apache.ignite.tcservice.model.user.User;
import org.apache.ignite.tcbot.common.exeption.ServiceUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Real implementation of login based on getting current user preferences from the server.
 */
public class TcLoginImpl implements ITcLogin {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcLoginImpl.class);

    /** Teamcity connection non-caching factory. */
    @Inject private Provider<TeamcityServiceConnection> tcFactory;

    /** {@inheritDoc} */
    @Override public User checkServiceUserAndPassword(String srvId, String username, String pwd) {
        try {
            TeamcityServiceConnection tcConn = tcFactory.get();

            tcConn.init(srvId);

            tcConn.setAuthData(username, pwd);

            final User tcUser = tcConn.getUserByUsername(username);

            if (tcUser != null)
                logger.info("TC user returned: " + tcUser);

            return tcUser;
        }
        catch (ServiceUnauthorizedException e) {
            final String msg = "Service " + srvId + " rejected credentials from " + username;
            System.err.println(msg);

            logger.warn(msg, e);
            return null;
        }
        catch (Exception e) {
            e.printStackTrace();
            logger.error("Unexpected login exception", e);

            return null;
        }
    }
}
