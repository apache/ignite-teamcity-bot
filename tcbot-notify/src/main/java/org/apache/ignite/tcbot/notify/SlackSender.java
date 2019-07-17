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

package org.apache.ignite.tcbot.notify;

import com.google.common.base.Preconditions;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
public class SlackSender implements ISlackSender {
    /** {@inheritDoc} */
    @Override public void sendMessage(String addr, String msg,
        ISlackBotConfig cfg) throws IOException {
        String authTok = cfg.slackAuthToken();
        Preconditions.checkState(!isNullOrEmpty(authTok), "notifications:\"{}\" property should be filled in branches.json");

        SlackSession ses = SlackSessionFactory.createWebSocketSlackSession(authTok);

        ses.connect();

        try {
            if (addr.startsWith("#")) {
                String ch = addr.substring(1);

                SlackChannel slackCh = ses.findChannelByName(ch);

                if (slackCh == null)
                    throw new RuntimeException("Failed to find channel [" + addr + "]: Notification not send [" + msg + "]");

                SlackMessageHandle<SlackMessageReply> handle = ses.sendMessage(slackCh, msg);

                System.out.println("Message to channel " + addr + " " + msg + "; acked: " + handle.isAcked());
            }
            else {
                SlackUser user = ses.findUserByUserName(addr); //make sure bot is a member of the user.

                if (user == null)
                    throw new RuntimeException("Failed to find user [" + addr + "]: Notification not send [" + msg + "]");

                SlackMessageHandle<SlackMessageReply> handle = ses.sendMessageToUser(user, msg, null);

                System.out.println("Message to user " + addr + " " + msg + "; acked: " + handle.isAcked());
            }
        }
        finally {
            ses.disconnect();
        }
    }
}
