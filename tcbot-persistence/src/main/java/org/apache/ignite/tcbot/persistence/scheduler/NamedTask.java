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
package org.apache.ignite.tcbot.persistence.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

class NamedTask {
    private final StampedLock lock = new StampedLock();
    private final String name;

    @GuardedBy("lock")
    private volatile Runnable cmd;

    @GuardedBy("lock")
    private volatile long lastFinishedTs = 0;

    @GuardedBy("lock")
    private volatile Status status = Status.CREATED;

    @GuardedBy("lock")
    private volatile long resValidityMs = 0;

    enum Status {
        CREATED, RUNNING, COMPLETED;
    }

    public NamedTask(String name) {
        this.name = name;
    }

    public void sheduleWithQuitePeriod(@Nonnull Runnable cmd, long period, TimeUnit unit) {
        long resValidityMs = unit.toMillis(period);

        boolean canSkip = false;

        long optReadStamp = lock.tryOptimisticRead();

        if (status == Status.RUNNING)
            canSkip = true;

        if (this.resValidityMs == 0 || this.resValidityMs > resValidityMs)
            canSkip = false;

        boolean optRead = lock.validate(optReadStamp);

        if (optRead && canSkip)
            return;

        long writeLockStamp = lock.writeLock();
        try {
            this.cmd = cmd;
            if (this.resValidityMs != 0)
                this.resValidityMs = Math.min(this.resValidityMs, resValidityMs);
            else
                this.resValidityMs = resValidityMs;
        }
        finally {
            lock.unlock(writeLockStamp);
        }

    }

    public Runnable runIfNeeded() throws Exception {
        long optReadStamp = lock.tryOptimisticRead();
        boolean canSkip = canSkipStartNow();
        boolean optRead = lock.validate(optReadStamp);

        if (optRead) {
            if (canSkip)
                return null;
        }
        else {
            long readStamp = lock.readLock();
            boolean canSkipStartNow;

            try {
                canSkipStartNow = canSkipStartNow();
            }
            finally {
                lock.unlockRead(readStamp);
            }

            if (canSkipStartNow)
                return null;
        }

        Runnable cmd;
        long writeLockStamp = lock.writeLock();
        try {
            cmd = this.cmd;
            this.cmd = null;

            // because here lock is not upgraded from read lock cmd may come here with null
            if (cmd != null)
                status = Status.RUNNING;
        }
        finally {
            lock.unlock(writeLockStamp);
        }

        if (cmd == null)
            return null;

        try {
            cmd.run();
        }
        finally {
            long writeLockStamp2 = lock.writeLock();
            try {
                lastFinishedTs = System.currentTimeMillis();
                status = Status.COMPLETED;
            }
            finally {
                lock.unlock(writeLockStamp2);
            }
        }

        return cmd;
    }

    public boolean canSkipStartNow() {
        boolean canSkip = false;
        if (status == Status.RUNNING)
            canSkip = true;

        if (status == Status.COMPLETED) {
            if (cmd == null)
                canSkip = true; // No one asked to run

            if (lastFinishedTs != 0 && (System.currentTimeMillis() - lastFinishedTs) < resValidityMs) {
                //result is still fresh
                canSkip = true;
            }
        }
        return canSkip;
    }

}
