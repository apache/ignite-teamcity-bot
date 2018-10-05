package org.apache.ignite.ci.observer;

import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.user.ICredentialsProv;

public abstract class Info {
    /** Finished. */
    public static final String FINISHED = "finished";

    /** Server id. */
    public final String srvId;

    /** Build type id. */
    public final String buildTypeId;

    /** Branch name. */
    public final String branchName;

    /** */
    public final ICredentialsProv prov;

    /** JIRA ticket full name. */
    public final String ticket;

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticket Ticket.
     * @param buildTypeId Build type id.
     * @param branchName Branch name.
     */
    public Info(String srvId, ICredentialsProv prov, String ticket, String buildTypeId, String branchName) {
        this.srvId = srvId;
        this.prov = prov;
        this.ticket = ticket;
        this.buildTypeId = buildTypeId;
        this.branchName = branchName;
    }

    /**
     * @param teamcity Teamcity.
     */
    public abstract boolean isFinished(IAnalyticsEnabledTeamcity teamcity);

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof Info))
            return false;

        Info info = (Info)o;

        return Objects.equals(srvId, info.srvId) &&
            Objects.equals(buildTypeId, info.buildTypeId) &&
            Objects.equals(branchName, info.branchName) &&
            Objects.equals(prov, info.prov) &&
            Objects.equals(ticket, info.ticket);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {

        return Objects.hash(srvId, buildTypeId, branchName, prov, ticket);
    }
}
