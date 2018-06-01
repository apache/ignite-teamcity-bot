package org.apache.ignite.ci.runners;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import org.apache.ignite.ci.HelperConfig;

import java.io.IOException;
import java.util.Properties;

public class SlackTest {
    public static void main(String[] args) throws IOException {

        Properties cfgProps = HelperConfig.loadEmailSettings();
        String authTok = HelperConfig.getMandatoryProperty(cfgProps, HelperConfig.SLACK_AUTH_TOKEN, HelperConfig. MAIL_PROPS);

        SlackSession session = SlackSessionFactory.createWebSocketSlackSession(authTok);
        session.connect();

        SlackUser channel = session.findUserByUserName("dpavlov"); //make sure bot is a member of the channel.
        session.sendMessageToUser(channel, "hi im a bot " +
                "<http://localhost:8080|app>", null);

        session.disconnect();
    }
}
