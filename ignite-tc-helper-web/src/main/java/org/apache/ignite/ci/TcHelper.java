package org.apache.ignite.ci;

import com.google.common.base.Strings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.detector.IssueDetector;
import org.apache.ignite.ci.detector.IssuesStorage;
import org.apache.ignite.ci.web.TcUpdatePool;

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

    public TcHelper(Ignite ignite) {
        this.ignite = ignite;

        issuesStorage = new IssuesStorage(ignite);

        detector = new IssueDetector(ignite, issuesStorage);
    }

    @Override public IAnalyticsEnabledTeamcity server(String serverId) {
        if(stop.get())
            throw new IllegalStateException("Shutdown");

        return servers.computeIfAbsent(Strings.nullToEmpty(serverId),
            k -> {
                IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite,
                    Strings.emptyToNull(serverId));
                
                teamcity.setExecutor(getService());

                return teamcity;
            });
    }

    @Override
    public IssuesStorage issues() {
        return issuesStorage;
    }

    @Override
    public IssueDetector issueDetector() {
        return detector;
    }

    public void close() {
        if(stop.compareAndSet(false, true)){
            servers.values().forEach(v -> {
                try {
                    v.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        tcUpdatePool.stop();
    }

    public ExecutorService getService() {
        return tcUpdatePool.getService();
    }
}
