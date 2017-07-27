package org.apache.ignite.ci.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by dpavlov on 27.07.2017
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Build {
    @XmlAttribute
    private String id;

    @XmlAttribute
    private String buildTypeId;

    @XmlAttribute
    private String href;

    public String getId() {
        return id;
    }
}
