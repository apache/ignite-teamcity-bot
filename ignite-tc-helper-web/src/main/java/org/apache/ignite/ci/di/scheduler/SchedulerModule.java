package org.apache.ignite.ci.di.scheduler;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;

public class SchedulerModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IScheduler.class).to(TcBotScheduler.class).in(new SingletonScope());
    }
}
