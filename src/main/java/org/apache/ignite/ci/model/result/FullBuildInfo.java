package org.apache.ignite.ci.model.result;

import com.google.common.base.Strings;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;

/**
 * Created by dpavlov on 27.07.2017
 */
@XmlRootElement(name = "build")
@XmlAccessorType(XmlAccessType.FIELD)
public class FullBuildInfo extends Build {
    @XmlElement(name = "buildType") BuildType buildType;

    @XmlElement public String queuedDate;
    @XmlElement public String startDate;
    @XmlElement public String finishDate;

    @XmlElement(name = "build")
    @XmlElementWrapper(name = "snapshot-dependencies")
    private List<Build> snapshotDependencies;

    @XmlElement(name = "problemOccurrences") public ProblemOccurrences problemOccurrences;

    @XmlElement(name = "testOccurrences") public TestOccurrencesRef testOccurrences;

    public List<Build> getSnapshotDependenciesNonNull() {
        return snapshotDependencies == null ? Collections.emptyList() : snapshotDependencies;
    }

    public String suiteName() {
        return buildType == null ? null : buildType.getName();
    }

    public String getFinishDateDdMmYyyy() throws ParseException {
        Date parse = getFinishDate();
        return new SimpleDateFormat("dd.MM.yyyy").format(parse);
    }

    public Date getFinishDate() {
        try {
            String date = finishDate;
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
            return f.parse(date);
        }
        catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasFinishDate() {
        return !Strings.isNullOrEmpty(finishDate);
    }
}
