package org.apache.ignite.ci.tcmodel.conf;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Build type reference
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildType {
    @XmlAttribute private String id;

    @XmlAttribute private String name;

    @XmlAttribute private String projectId;

    @XmlAttribute private String projectName;

    @XmlAttribute
    private String href;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }
}
