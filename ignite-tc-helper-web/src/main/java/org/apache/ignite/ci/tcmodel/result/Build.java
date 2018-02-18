package org.apache.ignite.ci.tcmodel.result;

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
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.changes.ChangesListRef;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;

/**
 * Build from history with test and problems references
 */
@XmlRootElement(name = "build")
@XmlAccessorType(XmlAccessType.FIELD)
public class Build extends BuildRef implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;
    @XmlElement(name = "buildType") BuildType buildType;

    @XmlElement public String queuedDate;
    @XmlElement public String startDate;
    @XmlElement public String finishDate;

    @XmlElement(name = "build")
    @XmlElementWrapper(name = "snapshot-dependencies")
    private List<BuildRef> snapshotDependencies;

    @XmlElement(name = "problemOccurrences") public ProblemOccurrencesRef problemOccurrences;

    @XmlElement(name = "testOccurrences") public TestOccurrencesRef testOccurrences;

    @XmlElement(name = "statistics") public StatisticsRef statisticsRef;

    /** Changes not included into build.*/
    @XmlElement(name = "lastChanges") public ChangesList lastChanges;

    /** Changes included into build.*/
    @XmlElement(name = "changes") public ChangesListRef changesRef;

    public Integer _version = LATEST_VERSION;

    public List<BuildRef> getSnapshotDependenciesNonNull() {
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
            if (finishDate == null)
                return null;
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
            return f.parse(finishDate);
        }
        catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasFinishDate() {
        return !Strings.isNullOrEmpty(finishDate);
    }

    public BuildType getBuildType() {
        return buildType;
    }

    @Override public int version() {
        return _version == null ? 0 : _version;
    }

    @Override public int latestVersion() {
        return LATEST_VERSION;
    }
}
