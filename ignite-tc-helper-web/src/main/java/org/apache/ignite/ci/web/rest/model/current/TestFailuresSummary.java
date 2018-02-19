package org.apache.ignite.ci.web.rest.model.current;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.web.IBackgroundUpdatable;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 * Created by dpavlov on 25.10.2017
 *
 * Summary failures from all servers
 */
@SuppressWarnings("WeakerAccess")
public class TestFailuresSummary extends AbstractTestMetrics implements IBackgroundUpdatable {

    /** Update required, set by background updater. */
    public boolean updateRequired = false;

    /** Running updates is in progress, summary is ready, but it is subject to change */
    public int runningUpdates = 0;

    /** Hash code hexadecimal, protects from redraw and minimizing mode info in case data not changed */
    public String hashCodeHex;

    public List<ChainAtServerCurrentStatus> servers = new ArrayList<>();

    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }

    public void addChainOnServer(ChainAtServerCurrentStatus chainStatus) {
        servers.add(chainStatus);

        if(chainStatus.failedToFinish!=null) {
            if (failedToFinish == null)
                failedToFinish = 0;
            failedToFinish += chainStatus.failedToFinish;
        }

        if(chainStatus.failedTests!=null) {
            if (failedTests == null)
                failedTests = 0;
            failedTests += chainStatus.failedTests;
        }
    }

    public void postProcess(int running) {
        runningUpdates = running;

        hashCodeHex = Integer.toHexString(U.safeAbs(hashCode()));
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestFailuresSummary summary = (TestFailuresSummary)o;
        return Objects.equal(servers, summary.servers);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(servers);
    }
}
