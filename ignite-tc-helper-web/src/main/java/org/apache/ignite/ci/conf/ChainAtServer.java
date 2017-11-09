package org.apache.ignite.ci.conf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by Дмитрий on 09.11.2017.
 */
public class ChainAtServer {
    /** Server ID to access config files within helper */
    @Nullable public String serverId;

    /** Suite identifier by teamcity identification for root chain */
    @Nonnull public String suiteId;

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ChainAtServer server = (ChainAtServer)o;

        if (serverId != null ? !serverId.equals(server.serverId) : server.serverId != null)
            return false;
        return suiteId.equals(server.suiteId);
    }

    @Override public int hashCode() {
        int result = serverId != null ? serverId.hashCode() : 0;
        result = 31 * result + suiteId.hashCode();
        return result;
    }
}
