package org.apache.ignite.ci.conf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Created by Дмитрий on 05.11.2017.
 */
public class ChainAtServerTracked {
    /** Server ID to access config files within helper */
    @Nullable public String serverId;

    /** Suite identifier by teamcity identification */
    @Nonnull public String suiteId;

    /** Branch identifier by TC identification for REST api */
    @Nonnull public String branchForRest;

    @Nonnull public String getSuiteIdMandatory() {
        checkState(!isNullOrEmpty(suiteId), "Invalid config: suiteId should be filled " + this);
        return suiteId;
    }

    @Nonnull public String getBranchForRestMandatory() {
        checkState(!isNullOrEmpty(branchForRest), "Invalid config: branchForRest should be filled " + this);
        return branchForRest;
    }
}
