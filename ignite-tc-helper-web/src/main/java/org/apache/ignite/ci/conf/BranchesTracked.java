package org.apache.ignite.ci.conf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by Дмитрий on 05.11.2017.
 */
public class BranchesTracked {
    public List<BranchTracked> branches = new ArrayList<>();

    public List<String> getIds() {
        return branches.stream().map(BranchTracked::getId).collect(Collectors.toList());
    }

    public Optional<BranchTracked> get(String branch) {
        return branches.stream().filter(b -> branch.equals(b.getId())).findAny();
    }

    public BranchTracked getBranchMandatory(String branch) {
        return get(branch).orElseThrow(() -> new RuntimeException("Branch not found: " + branch));
    }
}
