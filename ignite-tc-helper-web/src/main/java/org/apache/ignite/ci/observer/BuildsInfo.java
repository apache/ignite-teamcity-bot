package org.apache.ignite.ci.observer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

public class BuildsInfo extends Info {
    /** Finished builds. */
    private final Map<Build, Boolean> finishedBuilds = new HashMap<>();

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticket Ticket.
     * @param builds Builds.
     */
    public BuildsInfo(String srvId, ICredentialsProv prov, String ticket, Build[] builds) {
        super(srvId, prov, ticket, "IgniteTests24Java8_RunAll", builds[0].branchName);

        for (Build build : builds)
            finishedBuilds.put(build, false);
    }

    /** {@inheritDoc} */
    @Override public boolean isFinished(IAnalyticsEnabledTeamcity teamcity) {
        for (Map.Entry<Build, Boolean> entry : finishedBuilds.entrySet()){
            if (!entry.getValue()){
                Build build = teamcity.getBuild(entry.getKey().getId());
                entry.setValue(build.state.equals(FINISHED));
            }
        }

        return !finishedBuilds.containsValue(false);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildsInfo))
            return false;

        if (!super.equals(o))
            return false;

        BuildsInfo info = (BuildsInfo)o;

        return Objects.equals(finishedBuilds, info.finishedBuilds);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {

        return Objects.hash(super.hashCode(), finishedBuilds);
    }
}
