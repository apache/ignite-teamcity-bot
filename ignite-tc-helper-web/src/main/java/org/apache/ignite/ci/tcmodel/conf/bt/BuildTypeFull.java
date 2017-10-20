package org.apache.ignite.ci.tcmodel.conf.bt;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.jetbrains.annotations.Nullable;

/**
 * Full build type settings
 */
@XmlRootElement(name = "buildType")
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildTypeFull extends BuildType {
    @XmlElement(name = "parameters")
    Parameters parameters;

    @Nullable
    public String getParameter(String key) {
        if (parameters == null)
            return null;
        return parameters.getParameter(key);
    }
}
