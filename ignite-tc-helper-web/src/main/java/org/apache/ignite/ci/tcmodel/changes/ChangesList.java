package org.apache.ignite.ci.tcmodel.changes;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.db.Persisted;

/**
 * Created by Дмитрий on 18.02.2018.
 */
@XmlRootElement(name = "changes")
@Persisted
public class ChangesList extends ChangesListRef {
    @XmlElement(name = "change")
    public List<ChangeRef> changes;

    @XmlElement Integer count;

    @Override public String toString() {
        return "ChangesList{" +
            "changes=" + changes +
            '}';
    }
}
