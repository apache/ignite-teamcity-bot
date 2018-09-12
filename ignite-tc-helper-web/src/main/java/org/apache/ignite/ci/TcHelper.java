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

package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TC Bot implementation
 */
public class TcHelper implements ITcHelper {
    /** Stop guard. */
    private AtomicBoolean stop = new AtomicBoolean();

    private final Cache<String, IAnalyticsEnabledTeamcity> srvs
        = CacheBuilder.<String, String>newBuilder()
        .maximumSize(100)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();

    private Ignite ignite;
    private TcUpdatePool tcUpdatePool = new TcUpdatePool();
    private IssuesStorage issuesStorage;
    private IssueDetector detector;

    /** Build observer. */
    private BuildObserver buildObserver;

    private UserAndSessionsStorage userAndSessionsStorage;

    public TcHelper(Ignite ignite) {
        this.ignite = ignite;

        issuesStorage = new IssuesStorage(ignite);
        userAndSessionsStorage = new UserAndSessionsStorage(ignite);

        detector = new IssueDetector(ignite, issuesStorage, userAndSessionsStorage);
        buildObserver = new BuildObserver(this);
    }

    /** {@inheritDoc} */
    @Override public IssuesStorage issues() {
        return issuesStorage;
    }

    /** {@inheritDoc} */
    @Override public IssueDetector issueDetector() {
        return detector;
    }

    /** {@inheritDoc} */
    @Override public BuildObserver buildObserver() {
        return buildObserver;
    }

    /** {@inheritDoc} */
    @Override public IAnalyticsEnabledTeamcity server(String srvId, @Nullable ICredentialsProv prov) {
        if (stop.get())
            throw new IllegalStateException("Shutdown");

        Callable<IAnalyticsEnabledTeamcity> call = () -> {
            IAnalyticsEnabledTeamcity teamcity = new IgnitePersistentTeamcity(ignite,
                Strings.emptyToNull(srvId));

            teamcity.setExecutor(getService());

            if (prov != null) {
                final String user = prov.getUser(srvId);
                final String password = prov.getPassword(srvId);
                teamcity.setAuthData(user, password);
            }

            return teamcity;
        };
        String fullKey = Strings.nullToEmpty(prov == null ? null : prov.getUser(srvId)) + ":" + Strings.nullToEmpty(srvId);

        IAnalyticsEnabledTeamcity teamcity;
        try {
            teamcity = srvs.get(fullKey, call);
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        return teamcity;
    }

    /** {@inheritDoc} */
    @Override public ITcAnalytics tcAnalytics(String srvId) {
        return server(srvId, null);
    }

    /** {@inheritDoc} */
    @Override public UserAndSessionsStorage users() {
        return userAndSessionsStorage;
    }

    @Override public String primaryServerId() {
        return "apache"; //todo remove
    }

    //todo get from persistence
    public Collection<String> getServerIds() {
        return getTrackedBranches().getServerIds();
    }

    private BranchesTracked getTrackedBranches() {
        return HelperConfig.getTrackedBranches();
    }

    @Override public List<String> getTrackedBranchesIds() {
        return getTrackedBranches().getIds();
    }

    public void close() {
        if (stop.compareAndSet(false, true)) {
            srvs.asMap().values().forEach(v -> {
                try {
                    v.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        tcUpdatePool.stop();

        detector.stop();

        buildObserver.stop();
    }

    public ExecutorService getService() {
        return tcUpdatePool.getService();
    }
}
