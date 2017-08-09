package org.apache.ignite.ci.model.hist;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by dpavlov on 27.07.2017
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Build {
    @XmlAttribute private String id;

    @XmlAttribute private String buildTypeId;

    @XmlAttribute public String branchName;

    @XmlAttribute public String status;

    @XmlAttribute private String state;

    @XmlAttribute public String href;

    public String getId() {
        return id;
    }

    public int getIdAsInt() {
        return Integer.parseInt(getId());
    }
}
