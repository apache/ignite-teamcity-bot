package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.web.IBackgroundUpdatable;

/**
 * Created by dpavlov on 25.10.2017
 *
 * Summary failures from all servers
 */
@SuppressWarnings("WeakerAccess") public class TestFailuresSummary extends AbstractTestMetrics implements IBackgroundUpdatable {

    public boolean updateRequired = false;

    public List<ChainAtServerCurrentStatus> servers = new ArrayList<>();

    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }
}
