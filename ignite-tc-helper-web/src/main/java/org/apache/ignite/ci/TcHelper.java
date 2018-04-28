package org.apache.ignite.ci;

import com.google.common.base.Strings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.web.TcUpdatePool;

/**
 * Created by Дмитрий on 25.02.2018
 */
public class TcHelper implements ITcHelper {
    private AtomicBoolean stop = new AtomicBoolean();
    private ConcurrentHashMap<String, IAnalyticsEnabledTeamcity> servers = new ConcurrentHashMap<>();
    private Ignite ignite;
    private TcUpdatePool tcUpdatePool = new TcUpdatePool();

    public TcHelper(Ignite ignite) {
        this.ignite = ignite;
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
