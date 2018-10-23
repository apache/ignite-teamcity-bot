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

import java.util.Collection;
import java.util.Objects;
import java.util.Timer;
import javax.inject.Inject;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

/**
 *
 */
public class BuildObserver {
    /** Time between observing actions in milliseconds. */
    private static final long PERIOD = 10 * 60 * 1_000;

    /** Timer. */
    private final Timer timer;

    /** Task, which should be done periodically. */
    private ObserverTask observerTask;

    /**
     */
    @Inject
    public BuildObserver(ObserverTask observerTask) {
        timer = new Timer();

        timer.schedule(observerTask, 0, PERIOD);

        this.observerTask = observerTask;
    }

    /**
     * Stop observer.
     */
    public void stop() {
        timer.cancel();
    }

    /**
     * @param srvId Server id.
     * @param prov Credentials.
     * @param ticket JIRA ticket name.
     */
    public void observe(String srvId, ICredentialsProv prov, String ticket, Build... builds) {
        observerTask.addBuild(new BuildsInfo(srvId, prov, ticket, builds));
    }

    /**
     * @param srvId Server id.
     * @param branch Branch.
     */
    public String getObservationStatus(String srvId, String branch) {
        StringBuilder sb = new StringBuilder();
        Collection<BuildsInfo> builds = observerTask.getBuilds();

        for (BuildsInfo bi : builds) {
            if (Objects.equals(bi.branchName, branch)
                && Objects.equals(bi.srvId, srvId)) {
                sb.append(bi.ticket).append(" to be commented, waiting for builds. ");
                sb.append(bi.finishedBuildsCount());
                sb.append(" builds done from ");
                sb.append(bi.buildsCount());
            }
        }

        return sb.toString();
    }
}
