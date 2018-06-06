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

                //todo tmp
                return false;
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
