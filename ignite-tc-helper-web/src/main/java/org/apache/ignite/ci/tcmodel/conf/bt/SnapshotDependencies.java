package org.apache.ignite.ci.tcmodel.conf.bt;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * Created by dpavlov on 01.11.2017.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SnapshotDependencies {
    @XmlAttribute Integer count;


    @XmlElement(name="snapshot-dependency")
    List<SnapshotDependency> list = new ArrayList<>();
}
