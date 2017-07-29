package org.apache.ignite.ci.runners;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.model.BuildType;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Generates html with status builds
 */
public class GenerateStatusHtml {

    public static final String ENDL = String.format("%n");

    static class Branch {
        final String idForRest;
        final String idForUrl;
        final String displayName;

        Branch(String idForRest, String idForUrl, String displayName) {
            this.idForRest = idForRest;
            this.idForUrl = idForUrl;
            this.displayName = displayName;
        }
    }

    static class BuildStatusHref {
        final String statusImageSrc;
        final String hrefToBuild;

        BuildStatusHref(String src, String hrefToBuild) {
            this.statusImageSrc = src;
            this.hrefToBuild = hrefToBuild;
        }
    }

    static class SuiteStatus {
        Map<String, BuildStatusHref> branchIdToStatusUrl = new TreeMap<>();
        String suiteId;

        public SuiteStatus(String suiteId) {

            this.suiteId = suiteId;
        }
    }

    static class ProjectStatus {
        Map<String, SuiteStatus> suiteIdToStatusUrl = new TreeMap<>();

        public SuiteStatus suite(String id, String name) {
            return suiteIdToStatusUrl.computeIfAbsent(name, suiteId -> new SuiteStatus(id));
        }
    }

    public static void main(String[] args) throws Exception {
        final List<Branch> branchesPriv = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch("ignite-2.1.3", "ignite-2.1.3", "ignite-2.1.3"));
        final String tcPrivId = "private";
        final String privProjectId = "id8xIgniteGridGainTests";
        final ProjectStatus privStatuses = getBuildStatuses(tcPrivId, privProjectId, branchesPriv);

        final List<Branch> branchesPub = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch(
                //"pull%2F2296%Ffhead",
                "ignite-2.1.3",
                "pull/2296/head", "ignite-2.1.3"));
        final String pubTcId = "public";
        final String projectId = "Ignite20Tests";
        ProjectStatus pubStatus = getBuildStatuses(pubTcId, projectId, branchesPub);

        Properties privResp = HelperConfig.loadPrefixedProperties(tcPrivId, HelperConfig.RESP_FILE_NAME);
        Properties pubResp = HelperConfig.loadPrefixedProperties(pubTcId, HelperConfig.RESP_FILE_NAME);

        TreeSet<String> respPersons = allRespPersons(privResp, pubResp);
        System.err.println(respPersons);

        try (FileWriter writer = new FileWriter("status.html")) {
            line(writer, "<html>");
            header(writer);
            line(writer, "<body>");

            line(writer, "<div id=\"tabs\">");
            line(writer, "<ul>");

            for (String resp : respPersons) {
                String dispId = getDivId(resp);
                line(writer, "<li><a href=\"#" + dispId + "\">" + dispId + "</a></li>");
            }
            line(writer, "</ul>");

            for (String resp : respPersons) {
                line(writer, "<div id='" + getDivId(resp) + "'>");
                Predicate<String> filterPub = buildId -> {
                    return resp.equals(normalize(pubResp.getProperty(normalize(buildId))));
                };
                Predicate<String> filterPriv = buildId -> {
                    return resp.equals(normalize(privResp.getProperty(normalize(buildId))));
                };
                writeBuildsTable(branchesPriv, privStatuses, writer, filterPriv);

                writeBuildsTable(branchesPub, pubStatus, writer, filterPub);

                line(writer, "</div>");
            }
            line(writer, "</div>");
            line(writer, "\n");
            line(writer, "</body>");
            line(writer, "</html>");
        }
    }

    private static String normalize(String property) {
        return Strings.nullToEmpty(property).trim();
    }

    private static String getDivId(String resp) {
        return Strings.isNullOrEmpty(resp) ? "unassigned" : resp;
    }

    private static void line(FileWriter writer, String str) throws IOException {
        writer.write(str + ENDL);
    }

    private static TreeSet<String> allRespPersons(Properties privResp, Properties pubResp) {
        TreeSet<String> respPerson = new TreeSet<>();
        for (Object next : privResp.values()) {
            respPerson.add(Objects.toString(next));
        }
        for (Object next : pubResp.values()) {
            respPerson.add(Objects.toString(next));
        }
        return respPerson;
    }

    private static void header(FileWriter writer) throws IOException {
        writer.write("<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "  <title>Ignite Teamcity Build status</title>\n" +
            "  <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css\">\n" +
            "  <script src=\"https://code.jquery.com/jquery-1.12.4.js\"></script>\n" +
            "  <script src=\"https://code.jquery.com/ui/1.12.1/jquery-ui.js\"></script>\n" +
            "  <script>\n" +
            "  $( function() {\n" +
            "    $( \"#tabs\" ).tabs();\n" +
            "  } );\n" +
            "  </script>\n" +
            "</head>");
    }

    private static void writeBuildsTable(List<Branch> branchesPriv,
        ProjectStatus projectStatus,
        FileWriter writer,
        Predicate<String> includeBuildId) throws IOException {

        String endl = String.format("%n");
        line(writer, "<table>");

        writer.write("<tr>\n");
        writer.write("   <th>");
        writer.write("Build");
        writer.write("</th>\n");

        for (Branch next : branchesPriv) {
            writer.write("   <th>" + next.displayName + "</th>\n");
        }
        writer.write(" </tr>");

        Set<Map.Entry<String, SuiteStatus>> entries = projectStatus.suiteIdToStatusUrl.entrySet();
        for (Map.Entry<String, SuiteStatus> next : entries) {
            if(!includeBuildId.apply(next.getValue().suiteId)) {
                continue;
            }
            line(writer, "<tr>");
            line(writer, "<td>");
            writer.write(next.getKey());
            line(writer, "</td>");

            SuiteStatus branches1 = next.getValue();
            for (Map.Entry<String, BuildStatusHref> entry : branches1.branchIdToStatusUrl.entrySet()) {
                line(writer, "<td>");

                final BuildStatusHref statusHref = entry.getValue();
                writer.write("<a href='" + statusHref.hrefToBuild + "'/>" + endl);
                writer.write("<img src='" + statusHref.statusImageSrc + "'/>" + endl);
                line(writer, "</a>");
                line(writer, "</td>");
            }

            line(writer, "</tr>");
        }
        line(writer, "</table>");
    }

    private static ProjectStatus getBuildStatuses(
        final String tcId,
        final String projectId,
        final List<Branch> branchesPriv) throws Exception {

        ProjectStatus projStatus = new ProjectStatus();
        try (IgniteTeamcityHelper teamcityHelper = new IgniteTeamcityHelper(tcId)) {
            List<BuildType> suites = teamcityHelper.getProjectSuites(projectId).get();

            for (BuildType buildType : suites) {
                String id = buildType.getId();
                if (buildType.getName().startsWith("->"))
                    continue;

                for (Branch branch : branchesPriv) {
                    String branchIdRest = URLEncoder.encode(branch.idForRest, "UTF-8");
                    String branchIdUrl = URLEncoder.encode(branch.idForUrl, "UTF-8");
                    String url = teamcityHelper.host() +
                        "app/rest/builds/" +
                        "buildType:(id:" +
                        buildType.getId() +
                        ")" +
                        (isNullOrEmpty(branchIdRest) ? "" :
                            (",branch:(name:" + branchIdRest + ")")) +
                        "/statusIcon.svg";
                    //System.out.println(url);

                    SuiteStatus statusInBranches = projStatus.suite(buildType.getId(), buildType.getName());
                    final String href = teamcityHelper.host() +
                        "viewType.html" +
                        "?buildTypeId=" + buildType.getId() +
                        (isNullOrEmpty(branchIdUrl) ? "" : "&branch=" + branchIdUrl) +
                        "&tab=buildTypeStatusDiv";
                    statusInBranches.branchIdToStatusUrl.computeIfAbsent(branch.idForRest, k ->
                        new BuildStatusHref(url, href));
                }
            }
        }
        return projStatus;
    }
}
