package org.apache.ignite.ci.tcmodel.result;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Problem occurrences reference, short version with only reference
 */
public class ProblemOccurrencesRef {
    /** Href without host name to obtain full problems list. */
    @XmlAttribute public String href;
}
