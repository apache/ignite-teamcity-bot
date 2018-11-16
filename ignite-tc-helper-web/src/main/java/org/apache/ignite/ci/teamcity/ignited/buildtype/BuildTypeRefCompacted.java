package org.apache.ignite.ci.teamcity.ignited.buildtype;

import java.util.Objects;
import org.apache.ignite.ci.tcmodel.conf.BuildTypeRef;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.jetbrains.annotations.NotNull;

public class BuildTypeRefCompacted {
    /** Compacter identifier for string 'Id'. */
    private int id = -1;

    /** Compacter identifier for string 'Name'. */
    private int name = -1;

    /** Compacter identifier for string 'Project id'. */
    private int projectId = -1;

    /** Compacter identifier for string 'Project name'. */
    private int projectName = -1;

    public String name(IStringCompactor compactor) {
        return compactor.getStringFromId(name);
    }

    public String id(IStringCompactor compactor) {
        return compactor.getStringFromId(id);
    }

    public int id() {
        return id;
    }

    public int name() {
        return name;
    }

    public int projectId() {
        return projectId;
    }

    public int projectName() {
        return projectName;
    }

    /**
     * Default constructor.
     */
    public BuildTypeRefCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public BuildTypeRefCompacted(IStringCompactor compactor, BuildTypeRef ref) {
        id = compactor.getStringId(ref.getId());
        name = compactor.getStringId(ref.getName());
        projectId = compactor.getStringId(ref.getProjectId());
        projectName = compactor.getStringId(ref.getProjectName());
    }

    /**
     * @param refCompacted Reference compacted.
     */
    public BuildTypeRefCompacted(BuildTypeRefCompacted refCompacted) {
        id = refCompacted.id();
        name = refCompacted.name();
        projectId = refCompacted.projectId();
        projectName = refCompacted.projectName();
    }

    /**
     * @param compactor Compacter.
     */
    public BuildTypeRef toBuildTypeRef(IStringCompactor compactor) {
        BuildTypeRef res = new BuildTypeRef();

        fillBuildTypeRefFields(compactor, res);

        return res;
    }

    protected void fillBuildTypeRefFields(IStringCompactor compactor, BuildTypeRef res) {
        String id = id(compactor);
        res.setId(id);
        res.setName(compactor.getStringFromId(name));
        res.setProjectId(compactor.getStringFromId(projectId));
        res.setProjectName(compactor.getStringFromId(projectName));
        res.href = getHrefForId(id);
        res.setWebUrl(getWebUrlForId(id));
    }

    @NotNull protected static String getHrefForId(String id) {
        return "/app/rest/latest/builds/id:" + id;
    }

    @NotNull protected static String getWebUrlForId(String id) {
        return "http://ci.ignite.apache.org/viewType.html?buildTypeId=" + id;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildTypeRefCompacted))
            return false;

        BuildTypeRefCompacted compacted = (BuildTypeRefCompacted)o;

        return id == compacted.id &&
            name == compacted.name &&
            projectId == compacted.projectId &&
            projectName == compacted.projectName;
    }

    @Override public int hashCode() {
        return Objects.hash(id, name, projectId, projectName);
    }

    @Override public String toString() {
        return "BuildTypeRefCompacted{" +
            "id=" + id +
            ", name=" + name +
            ", projectId=" + projectId +
            ", projectName=" + projectName +
            '}';
    }
}
