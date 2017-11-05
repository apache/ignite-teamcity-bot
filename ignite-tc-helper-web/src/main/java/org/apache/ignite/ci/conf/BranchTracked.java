package org.apache.ignite.ci.conf;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Дмитрий on 05.11.2017.
 */
public class BranchTracked {
    /** ID for internal REST and for */
    public String id;

    public List<ChainAtServerTracked> chains = new ArrayList<>();

    public String getId() {
        return id;
    }
}
