package org.apache.ignite.ci.tcmodel.hist;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** List of builds from build history */
@XmlRootElement(name = "builds")
@XmlAccessorType(XmlAccessType.FIELD)
public class Builds {
    @XmlElement(name = "build")
    private List<BuildRef> builds;

    public List<BuildRef> getBuildsNonNull() {
        return builds == null ? Collections.emptyList() : builds;
    }

}
