package org.apache.ignite.tcbot.common.conf;

import java.util.Collection;

public interface ITcServerConfigSupplier {
    /**
     * @return all configured server identifiers, without relation to current user access.
     */
    public Collection<String> getConfiguredServerIds();

    public ITcServerConfig getTeamcityConfig(String srvCode);

}
