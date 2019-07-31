package org.apache.ignite.tcbot.engine.ui;

import com.google.common.base.Strings;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.history.IRunHistory;

public class ShortTestFailureUi {
    /** Test full Name */
    public String name;

    /** suite (in code) short name */
    @Nullable public String suiteName;

    /** test short name with class and method */
    @Nullable public String testName;


    /** Blocker comment: indicates test seems to be introduced failure. */
    @Nullable public String blockerComment;

    /**
     *
     */
    public boolean isPossibleBlocker() {
        return !Strings.isNullOrEmpty(blockerComment);
    }

    public ShortTestFailureUi initFrom(@Nonnull TestCompactedMult failure,
        ITeamcityIgnited tcIgn, Integer baseBranchId) {
        name = failure.getName();

        String[] split = Strings.nullToEmpty(name).split("\\:");
        if (split.length >= 2) {
            String suiteShort = split[0].trim();
            String[] suiteComps = suiteShort.split("\\.");
            if (suiteComps.length > 1)
                suiteName = suiteComps[suiteComps.length - 1];

            String testShort = split[1].trim();
            String[] testComps = testShort.split("\\.");
            if (testComps.length > 2)
                testName = testComps[testComps.length - 2] + "." + testComps[testComps.length - 1];
        }

        final IRunHistory stat = failure.history(tcIgn, baseBranchId);
        blockerComment = failure.getPossibleBlockerComment(stat);

        return this;
    }
}
