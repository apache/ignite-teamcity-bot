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

/**
 * Scheduler which never waits. Can be used for tests.
 */
public class DirectExecNoWaitScheduler implements IScheduler {
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
