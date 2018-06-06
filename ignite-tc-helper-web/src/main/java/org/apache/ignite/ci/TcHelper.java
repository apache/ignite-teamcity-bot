package org.apache.ignite.ci;

import com.google.common.base.Strings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.ignite.Ignite;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Дмитрий on 25.02.2018
 */
public class TcHelper implements ITcHelper {
    private AtomicBoolean stop = new AtomicBoolean();
    private ConcurrentHashMap<String, IAnalyticsEnabledTeamcity> servers = new ConcurrentHashMap<>();
    private Ignite ignite;
    private TcUpdatePool tcUpdatePool = new TcUpdatePool();
    private IssuesStorage issuesStorage;
    private IssueDetector detector;
    private UserAndSessionsStorage userAndSessionsStorage;

    public TcHelper(Ignite ignite) {
        this.ignite = ignite;

        issuesStorage = new IssuesStorage(ignite);
        userAndSessionsStorage = new UserAndSessionsStorage(ignite);

        detector = new IssueDetector(ignite, issuesStorage);
    }

    @Override
    public IssuesStorage issues() {
        return issuesStorage;
    }

    @Override
    public IssueDetector issueDetector() {
        return detector;
    }

    @Override
    public IAnalyticsEnabledTeamcity server(String srvId, @Nullable ICredentialsProv prov) {
        if (stop.get())
            throw new IllegalStateException("Shutdown");

        return servers.computeIfAbsent(
                Strings.nullToEmpty(prov == null ? null : prov.getUser(srvId)) + ":" + Strings.nullToEmpty(srvId),
                k -> {
                    IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite,
                            Strings.emptyToNull(srvId));

                    teamcity.setExecutor(getService());

                    if (prov != null) {
                        final String user = prov.getUser(srvId);
                        final String password = prov.getPassword(srvId);
                        teamcity.setAuthData(user, password);
                    }

                    return teamcity;
                });
    }

    @Override
    public ITcAnalytics tcAnalytics(String serverId) {
        return server(serverId, null);
    }

    @Override
    public UserAndSessionsStorage users() {
        return userAndSessionsStorage;
    }

    @Override
    public String primaryServerId() {
        return "public"; //todo remove
    }

    //todo get from persistence
    public Collection<String> getServerIds() {
        return HelperConfig.getTrackedBranches().getServerIds();
    }

    public void close() {
        if (stop.compareAndSet(false, true)) {
            servers.values().forEach(v -> {
                try {
                    v.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        tcUpdatePool.stop();

        detector.stop();
    }

    public ExecutorService getService() {
        return tcUpdatePool.getService();
    }
}
