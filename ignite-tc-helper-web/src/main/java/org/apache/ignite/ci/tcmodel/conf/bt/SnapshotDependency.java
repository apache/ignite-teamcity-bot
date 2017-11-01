package org.apache.ignite.ci.tcmodel.conf.bt;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import org.apache.ignite.ci.tcmodel.conf.BuildType;

/**
 * Created by dpavlov on 01.11.2017.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SnapshotDependency {
    @XmlAttribute String id;
    @XmlAttribute String type;

    @XmlElement(name = "properties")
    Parameters properties;

    @XmlElement(name = "source-buildType")
    BuildType srcBt;

    public String getProperty(String id) {
        if (properties == null)
            return null;
        return properties.getParameter(id);
    }

    public BuildType bt() {
        return srcBt;
    }
}
