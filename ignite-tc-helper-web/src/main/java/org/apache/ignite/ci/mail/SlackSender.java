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

package org.apache.ignite.ci.mail;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import org.apache.ignite.ci.HelperConfig;

import java.io.IOException;
import java.util.Properties;

public class SlackSender {
    public static void main(String[] args) throws IOException {
        String addr = "dpavlov";
        sendMessage(addr, "hi im a bot " +
            "<http://localhost:8080|app>");
    }

    public static boolean sendMessage(String addr, String message) throws IOException {
        Properties cfgProps = HelperConfig.loadEmailSettings();
        String authTok = HelperConfig.getMandatoryProperty(cfgProps, HelperConfig.SLACK_AUTH_TOKEN, HelperConfig. MAIL_PROPS);

        SlackSession session = SlackSessionFactory.createWebSocketSlackSession(authTok);
        session.connect();

        try {
            if(addr.startsWith("#")) {
                String channel = addr.substring(1);

                SlackChannel slackChannel = session.findChannelByName(channel);

                if (slackChannel == null) {
                    System.err.println("Failed to find channel [" + addr + "]: Notification not send [" + message + "]");

                    return false;
                }

                SlackMessageHandle<SlackMessageReply> handle = session.sendMessage(slackChannel, message);

                System.out.println("Message to channel " + addr + " "  + message + "; acked: " + handle.isAcked());
            }
            else {
                SlackUser user = session.findUserByUserName(addr); //make sure bot is a member of the user.

                if (user == null) {
                    System.err.println("Failed to find user [" + addr + "]: Notification not send [" + message + "]");

                    return false;
                }

                SlackMessageHandle<SlackMessageReply> handle = session.sendMessageToUser(user, message, null);

                System.out.println("Message to user " + addr + " "  + message + "; acked: " + handle.isAcked());

            }
        }
        finally {
            session.disconnect();
        }

        return true;
    }
}
