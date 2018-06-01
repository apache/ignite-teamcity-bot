package org.apache.ignite.ci.user;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.web.CtxListener;
import org.jetbrains.annotations.Nullable;

public class UserAndSessionsStorage {
    public static final String USERS = "users";
    public static final String USER_SESSIONS = "UserSessions";

    private Ignite ignite;

    public UserAndSessionsStorage(Ignite ignite) {
        this.ignite = ignite;
    }

    IgniteCache<String, TcHelperUser> users() {
        return ignite.getOrCreateCache(USERS);
    }

    @Nullable public UserSession getSession(String sessId) {
        return sessions().get(sessId);
    }


    private IgniteCache<String, UserSession> sessions() {
        return ignite.getOrCreateCache(USER_SESSIONS);
    }

    public void addSession(String sessId, UserSession userSession) {
        sessions().put(sessId, userSession);
    }
}
