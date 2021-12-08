package org.apache.ignite.ci.tcbot.conf;

import org.apache.ignite.tcbot.engine.conf.BranchTracked;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Inmem only implementation for tests
 */
public class MemoryTrackedBranches implements ITrackedBranchesConfig {
    /**
     * Branches.
     */
    private List<BranchTracked> branches = new ArrayList<>();

    @Override
    public Stream<ITrackedBranch> branchesStream() {
        return branches.stream().map(t -> t);
    }


    public void addBranch(BranchTracked branch) {
        branches.add(branch);
    }
}
