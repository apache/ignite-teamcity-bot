package org.apache.ignite.ci.tcmodel.vcs;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Revisions {
    @XmlAttribute(name = "count")
    private Integer count;

    @XmlElement(name = "revision")
    private List<Revision> revisions;

    @Nonnull
    public List<Revision> revisions() {
        if (revisions == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(revisions);
    }

    public void revisions(List<Revision> revisions) {
        this.revisions = revisions;
        this.count = revisions.size();
    }
}
