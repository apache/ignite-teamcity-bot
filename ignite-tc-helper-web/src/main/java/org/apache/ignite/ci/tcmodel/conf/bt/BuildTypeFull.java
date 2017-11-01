package org.apache.ignite.ci.tcmodel.conf.bt;

import java.util.Collections;
import java.util.List;
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

    @XmlElement(name = "snapshot-dependencies")
    SnapshotDependencies snapshotDependencies;

    @Nullable
    public String getParameter(String key) {
        if (parameters == null)
            return null;
        return parameters.getParameter(key);
    }

    public List<SnapshotDependency> dependencies() {
        if (snapshotDependencies == null)
            return Collections.emptyList();

        final List<SnapshotDependency> list = snapshotDependencies.list;
        if (list == null)
            return Collections.emptyList();

        return list;
    }
}
