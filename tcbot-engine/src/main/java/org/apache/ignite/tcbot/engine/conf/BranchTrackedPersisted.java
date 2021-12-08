package org.apache.ignite.tcbot.engine.conf;

import javax.annotation.Nullable;

public class BranchTrackedPersisted extends BranchTracked {
    @Nullable Boolean softDeleted;

    public static BranchTrackedPersisted initFrom(ITrackedBranch b) {
        BranchTrackedPersisted bp = new BranchTrackedPersisted();

        b.chainsStream().map(ChainAtServerTracked::initFrom).forEach(ct -> {
            bp.chains.add(ct);
        });

        return bp;
    }

}
