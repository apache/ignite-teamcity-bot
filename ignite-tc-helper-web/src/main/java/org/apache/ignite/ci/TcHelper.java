package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.ignite.Ignite;
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
 * Created by Дмитрий on 25.02.2018
 */
public class TcHelper implements ITcHelper {
    private AtomicBoolean stop = new AtomicBoolean();

    private final Cache<String, IAnalyticsEnabledTeamcity> servers
        = CacheBuilder.<String, String>newBuilder()
        .maximumSize(100)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();

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

        Callable<IAnalyticsEnabledTeamcity> callable = () -> {
            IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite,
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
            teamcity = servers.get(fullKey, callable);
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        return teamcity;
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
        return "apache"; //todo remove
    }

    //todo get from persistence
    public Collection<String> getServerIds() {
        return HelperConfig.getTrackedBranches().getServerIds();
    }

    public void close() {
        if (stop.compareAndSet(false, true)) {
            servers.asMap().values().forEach(v -> {
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
