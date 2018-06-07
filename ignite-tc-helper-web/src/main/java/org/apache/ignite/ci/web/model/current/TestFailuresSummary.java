package org.apache.ignite.ci.web.model.current;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.web.IBackgroundUpdatable;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.Nullable;

/**
 * Created by dpavlov on 25.10.2017
 *
 * Summary of failures from all servers.
 */
@SuppressWarnings("WeakerAccess") public class TestFailuresSummary extends UpdateInfo implements IBackgroundUpdatable {

    public List<ChainAtServerCurrentStatus> servers = new ArrayList<>();

    public Integer failedTests;

    /** Count of suites with critical build problems found */
    public Integer failedToFinish;

    /** Tracked branch ID. */
    @Nullable private String trackedBranch;

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

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestFailuresSummary summary = (TestFailuresSummary)o;
        return Objects.equal(servers, summary.servers) &&
            Objects.equal(failedTests, summary.failedTests) &&
            Objects.equal(failedToFinish, summary.failedToFinish);
        //todo add tracked branch to equals
    }

    @Override public int hashCode() {
        return Objects.hashCode(servers, failedTests, failedToFinish);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        StringBuilder builder = new StringBuilder();

        servers.forEach(
            s -> {
                if (s != null)
                    builder.append(s.toString());
            }
        );

        return builder.toString();
    }

    public void setTrackedBranch(String trackedBranch) {
        this.trackedBranch = trackedBranch;
    }

    @Nullable
    public String getTrackedBranch() {
        return trackedBranch;
    }
}
