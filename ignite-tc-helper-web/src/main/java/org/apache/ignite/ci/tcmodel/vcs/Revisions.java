package org.apache.ignite.ci.tcmodel.vcs;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Revisions {
    @XmlAttribute(name = "count")
    private Integer count;

    @XmlElement(name = "revision")
    List<Revision> revisions;
}
