package org.apache.ignite.ci.tcmodel.user;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.List;

@XmlRootElement(name = "users")
public class Users {
    @XmlElement(name = "user")
    private List<UserRef> users;

    public List<UserRef> getUsersRefs() {
        if (users == null)
            return Collections.emptyList();

        return users;

    }
}
