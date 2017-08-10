package org.apache.ignite.ci.model.hist;

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
    private List<Build> builds;

    public List<Build> getBuildsNonNull() {
        return builds == null ? Collections.emptyList() : builds;
    }

}
