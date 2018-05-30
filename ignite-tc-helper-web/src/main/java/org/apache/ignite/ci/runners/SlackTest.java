package org.apache.ignite.ci.runners;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;

import java.io.IOException;

public class SlackTest {
    public static void main(String[] args) throws IOException {
        SlackSession session = SlackSessionFactory.createWebSocketSlackSession("slack-bot-auth-token");
        session.connect();

        SlackChannel channel = session.findChannelByName("general"); //make sure bot is a member of the channel.
        session.sendMessage(channel, "hi im a bot" );

    }
}
