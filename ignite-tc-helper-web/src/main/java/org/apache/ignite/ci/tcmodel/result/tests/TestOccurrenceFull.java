package org.apache.ignite.ci.tcmodel.result.tests;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;

/**
 * Full test occurrence returned by
 * https://ci.ignite.apache.org/app/rest/latest/testOccurrences/id:5454,build:(id:931136)
 */
@XmlRootElement(name = "testOccurrence")
public class TestOccurrenceFull extends TestOccurrence {
    @XmlElement(name = "test") public TestRef test;

    @XmlElement public BuildRef build;

    /** Failure text details */
    @XmlElement public String details;
}
