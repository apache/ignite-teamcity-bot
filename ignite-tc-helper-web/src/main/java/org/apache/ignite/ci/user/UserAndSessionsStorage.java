package org.apache.ignite.ci.user;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.Nullable;

public class UserAndSessionsStorage {
    public static final String USERS = "users";
    public static final String USER_SESSIONS = "sessions";

    private Ignite ignite;

    public UserAndSessionsStorage(Ignite ignite) {
        this.ignite = ignite;
    }

    public IgniteCache<String, TcHelperUser> users() {
        return ignite.getOrCreateCache(getTxConfig(USERS));
    }

    @Nullable public UserSession getSession(String sessId) {
        return sessions().get(sessId);
    }


    private IgniteCache<String, UserSession> sessions() {
        return ignite.getOrCreateCache(getTxConfig(USER_SESSIONS));
    }

    private <K, V> CacheConfiguration<K, V> getTxConfig(String name) {
        return TcHelperDb.<K, V>getCacheV2Config(name).setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

    }

    public void putSession(String sessId, UserSession userSession) {
        sessions().put(sessId, userSession);
    }

    public TcHelperUser getUser(String username) {
        return users().get(username);
    }

    public void putUser(String username, TcHelperUser user) {
        users().put(username, user);
    }

}
