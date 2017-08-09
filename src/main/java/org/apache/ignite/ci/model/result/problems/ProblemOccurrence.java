package org.apache.ignite.ci.model.result.problems;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by dpavlov on 03.08.2017
 */
public class ProblemOccurrence {
    @XmlAttribute public String id;
    @XmlAttribute public String identity;
    @XmlAttribute public String type;
    @XmlAttribute public String href;
}
