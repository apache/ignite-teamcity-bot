package org.apache.ignite.ci.tcmodel.conf;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Build type reference
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildType {
    @XmlAttribute(name="id")
    private String id;

    @XmlAttribute(name="name")
    private String name;

    @XmlAttribute
    private String href;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }
}
