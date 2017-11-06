package org.apache.ignite.ci.tcmodel.result.tests;

import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.ci.tcmodel.result.AbstractRef;

/**
 * Created by Дмитрий on 06.11.2017
 */
public class TestRef extends AbstractRef {
    @XmlAttribute public Long id;
    @XmlAttribute public String name;
}
