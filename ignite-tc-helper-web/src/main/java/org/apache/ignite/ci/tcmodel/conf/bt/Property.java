package org.apache.ignite.ci.tcmodel.conf.bt;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import org.jetbrains.annotations.Nullable;

/**
 * Created by dpavlov on 20.10.2017.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Property {
    @XmlAttribute String name;
    @XmlAttribute String value;
    @XmlAttribute Boolean inherited;


    @Nullable
    public String getValue() {
        return value;
    }
}
