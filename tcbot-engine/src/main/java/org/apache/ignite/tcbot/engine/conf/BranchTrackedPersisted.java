package org.apache.ignite.tcbot.engine.conf;

import javax.annotation.Nullable;

public class BranchTrackedPersisted extends BranchTracked {
    @Nullable protected Boolean softDeleted;

    public static BranchTrackedPersisted initFrom(ITrackedBranch b) {
        BranchTrackedPersisted bp = new BranchTrackedPersisted();

        b.chainsStream().map(ChainAtServerTracked::initFrom).forEach(bp.chains::add);

        bp.disableIssueTypes = b.disableIssueTypes();
        bp.softDeleted = false;

        return bp;
    }

    public boolean isDeleted() {
        return softDeleted != null && softDeleted;
    }

    public boolean isAlive() {
        return !isDeleted();
    }

}
