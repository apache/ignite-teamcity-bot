package org.apache.ignite.ci.di.scheduler;

import java.util.concurrent.TimeUnit;

/**
 * Sheduler which never waits
 */
public class DirectExecNoWaitSheduler implements IScheduler {
    /** {@inheritDoc} */
    @Override public void invokeLater(Runnable cmd, long delay, TimeUnit unit) {
        cmd.run();
    }

    /** {@inheritDoc} */
    @Override public void sheduleNamed(String fullName, Runnable cmd, long queitPeriod, TimeUnit unit) {
        cmd.run();
    }

    /** {@inheritDoc} */
    @Override public void stop() {

    }
}
