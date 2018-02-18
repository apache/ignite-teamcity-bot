package org.apache.ignite.ci.tcmodel.changes;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import org.apache.ignite.ci.db.Persisted;

/**
 * Created by Дмитрий on 18.02.2018.
 */
@Persisted
public class ChangesList {
    @XmlElement(name = "change")
    public List<ChangeRef> changes;
}
