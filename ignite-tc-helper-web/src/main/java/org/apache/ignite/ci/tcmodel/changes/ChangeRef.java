package org.apache.ignite.ci.tcmodel.changes;

import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.result.AbstractRef;

/**
 * Created by Дмитрий on 18.02.2018.
 */
@Persisted
public class ChangeRef extends AbstractRef {
    @XmlAttribute public String id;
    @XmlAttribute public String version;
    @XmlAttribute public String username;
    @XmlAttribute public String date;
    @XmlAttribute public String webUrl;
}
