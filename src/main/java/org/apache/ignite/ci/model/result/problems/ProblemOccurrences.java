package org.apache.ignite.ci.model.result.problems;

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.ProblemOccurrencesRef;

/**
 * Created by dpavlov on 03.08.2017
 */
@XmlRootElement(name = "problemOccurrences")
public class ProblemOccurrences extends ProblemOccurrencesRef {
    @XmlElement(name = "problemOccurrence")
    private List<ProblemOccurrence> problemOccurrences;

    public List<ProblemOccurrence> getProblemsNonNull() {
        return problemOccurrences == null ? Collections.emptyList() : problemOccurrences;
    }
}
