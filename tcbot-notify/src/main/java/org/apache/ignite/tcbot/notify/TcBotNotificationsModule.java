package org.apache.ignite.tcbot.notify;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;

/**
 *
 */
public class TcBotNotificationsModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IEmailSender.class).to(EmailSender.class).in(new SingletonScope());
    }
}
