package org.apache.ignite.ci.tcmodel.user;


import org.apache.ignite.ci.tcmodel.conf.bt.Parameters;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "user")
public class User extends UserRef {
    @XmlAttribute
    public String email;
    @XmlAttribute
    public String lastLogin;

    @XmlElement(name = "parameters")
    Parameters parameters;

    /**
     * @return space separated list of vcs user names
     */
    @Nullable
    String getVcsNames() {
        if (parameters == null)
            return null;

        return parameters.getParameter("plugin:vcs:anyVcs:anyVcsRoot");
    }
}
