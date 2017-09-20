package org.apache.ignite.ci.model.result.tests;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.model.result.TestOccurrencesRef;

/**
 * Created by dpavlov on 03.08.2017
 */
@XmlRootElement(name = "testOccurrences")
public class TestOccurrences extends TestOccurrencesRef {
    @XmlElement(name = "testOccurrence")
    private List<TestOccurrence> testOccurrences;

    @XmlAttribute private String nextHref;

    public List<TestOccurrence> getTests() {
        return testOccurrences == null ? Collections.emptyList() : testOccurrences;
    }
}
