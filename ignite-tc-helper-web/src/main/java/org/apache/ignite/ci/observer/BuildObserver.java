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

package org.apache.ignite.ci.observer;

import java.util.Timer;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

/**
 *
 */
public class BuildObserver {
    /** Time between observing actions in milliseconds. */
    private final long period = 30 * 60 * 1_000;

    /** Timer. */
    private final Timer timer;

    /** Task, which should be done periodically. */
    private final ObserverTask task;

    /**
     * @param helper Helper.
     */
    public BuildObserver(ITcHelper helper) {
        timer = new Timer();

        timer.schedule(task = new ObserverTask(helper), period, period);
    }

    /**
     * Stop observer.
     */
    public void stop() {
        timer.cancel();
    }

    /**
     * @param build Build id.
     * @param srvId Server id.
     * @param prov Credentials.
     * @param ticket JIRA ticket name.
     */
    public void observe(Build build, String srvId, ICredentialsProv prov, String ticket) {
        task.builds.add(new BuildInfo(build, srvId, prov, ticket));
    }
}
