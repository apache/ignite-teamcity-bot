package org.apache.ignite.ci.tcmodel.user;

import javax.xml.bind.annotation.XmlAttribute;

public class UserRef {
    @XmlAttribute public String id;
    @XmlAttribute public String username;
    @XmlAttribute public String name;
    @XmlAttribute public String href;
}
