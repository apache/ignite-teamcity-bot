package org.apache.ignite.ci.tcmodel.result;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Test occurrences reference
 */
public class TestOccurrencesRef {
    /** Href without host name to obtain full tests list. */
    @XmlAttribute public String href;

    @XmlAttribute public Integer count;
    @XmlAttribute public Integer passed;
    @XmlAttribute public Integer failed;
    @XmlAttribute public Integer muted;
}
