package org.apache.ignite.ci.tcmodel.result.tests;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by dpavlov on 20.09.2017
 */
public class TestOccurrence {
    @XmlAttribute public String id;
    @XmlAttribute public String name;
    @XmlAttribute public String status;
    @XmlAttribute public Integer duration;
    @XmlAttribute public String href;

    @XmlAttribute public Boolean muted;
    @XmlAttribute public Boolean currentlyMuted;
    @XmlAttribute public Boolean currentlyInvestigated;
    @XmlAttribute public Boolean ignored;

    public String getName() {
        return name;
    }

    public boolean isFailedTest() {
        return !"SUCCESS".equals(status);
    }

    public boolean isMutedTest() {
        return (muted != null && muted) || (currentlyMuted != null && currentlyMuted);
    }

    public boolean isIgnoredTest() {
        return (ignored != null && ignored);
    }

    public boolean isNotMutedOrIgnoredTest() {
        return !isMutedTest() && !isIgnoredTest();
    }
}
