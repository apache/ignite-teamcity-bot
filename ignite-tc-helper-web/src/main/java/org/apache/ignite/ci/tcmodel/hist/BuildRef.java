package org.apache.ignite.ci.tcmodel.hist;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.ci.tcmodel.result.AbstractRef;

/**
 * Actual result of build execution from build history,
 * short version involved in snapshot dependencies and into build history list
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildRef extends AbstractRef {
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_SUCCESS = "SUCCESS";
    @XmlAttribute private Integer id;

    @XmlAttribute public String buildTypeId;

    @XmlAttribute public String branchName;

    @XmlAttribute public String status;

    public static final String STATE_FINISHED = "finished";

    public static final String STATE_RUNNING = "running";

    public static final String STATE_QUEUED = "queued";

    @XmlAttribute private String state;

    @XmlAttribute(name = "number") public String buildNumber;

    @XmlAttribute public Boolean defaultBranch;

    public Integer getId() {
        return id;
    }

    public boolean isNotCancelled() {
        return !hasUnknownStatus();
    }

    private boolean hasUnknownStatus() {
        return STATUS_UNKNOWN.equals(status);
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public String suiteId() {
        return buildTypeId;
    }
}
