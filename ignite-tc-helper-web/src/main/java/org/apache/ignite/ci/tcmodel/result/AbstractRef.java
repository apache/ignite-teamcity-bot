package org.apache.ignite.ci.tcmodel.result;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by Дмитрий on 06.11.2017.
 */
public abstract class AbstractRef {
    /** Href without host name to obtain full problems list. */
    @XmlAttribute public String href;
}
