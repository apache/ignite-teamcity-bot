package org.apache.ignite.ci.model.result;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Test occurrences reference
 */
public class TestOccurrencesRef {
    @XmlAttribute public String href;

    @XmlAttribute public Integer count;
    @XmlAttribute public Integer passed;
    @XmlAttribute public Integer failed;
    @XmlAttribute public Integer muted;
}
