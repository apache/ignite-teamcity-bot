package org.apache.ignite.ci.tcmodel.conf;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by dpavlov on 27.07.2017.
 */
@XmlRootElement(name="project")
@XmlAccessorType(XmlAccessType.FIELD)
public class Project {
    @XmlAttribute(name="name")
    private String name;

    @XmlElementWrapper(name="buildTypes")
    @XmlElement(name="buildType")
    private List<BuildType> buildTypes;

    public List<BuildType> getBuildTypesNonNull() {
        return buildTypes == null ? Collections.emptyList() : buildTypes;
    }
}

/*

    @XmlElementWrapper(name="Families")
    @XmlElement(name="Family")
    private List<Family> families;

    @XmlElementWrapper(name="RemainingChildList")
    @XmlElement(name="ChildID")
    private List<BuildType> remainingChildren;
*/

