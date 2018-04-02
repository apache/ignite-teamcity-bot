package org.apache.ignite.ci.conf;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Branch on server
 */
public class ChainAtServerTracked extends ChainAtServer {

    /** Branch identifier by TC identification for REST api */
    @Nonnull public String branchForRest;

    /** @return {@link #suiteId} */
    @Nonnull public String getSuiteIdMandatory() {
        checkState(!isNullOrEmpty(suiteId), "Invalid config: suiteId should be filled " + this);
        return suiteId;
    }

    /**
     * @return
     */
    @Nonnull public String getBranchForRestMandatory() {
        checkState(!isNullOrEmpty(branchForRest), "Invalid config: branchForRest should be filled " + this);

        return branchForRest;
    }
}
