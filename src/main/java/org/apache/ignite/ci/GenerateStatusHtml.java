package org.apache.ignite.ci;

import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.ignite.ci.model.BuildType;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Generates html with status builds
 */
public class GenerateStatusHtml {

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

    static class BuildStatusInBranches {
        Map<String, BuildStatusHref> branchIdToStatusUrl = new TreeMap<>();
    }

    public static void main(String[] args) throws Exception {


        final List<Branch> branchesPriv = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch( "ignite-2.1.3", "ignite-2.1.3", "ignite-2.1.3"));
        String tcPrivId = "private";
        String privProjectId = "id8xIgniteGridGainTests";
        Map<String, BuildStatusInBranches> privStatuses = getBuildStatuses(tcPrivId, privProjectId, branchesPriv);


        final List<Branch> branchesPub = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch(
                //"pull%2F2296%Ffhead",
                "ignite-2.1.3",
                "pull/2296/head", "ignite-2.1.3"));
        String tcId = "public";
        String projectId = "Ignite20Tests";
        Map<String, BuildStatusInBranches> statuses = getBuildStatuses(tcId, projectId, branchesPub);

        String endl = String.format("%n");
        try (FileWriter writer = new FileWriter("status.html")) {
            writer.write("<html>" + endl);
            header(writer);
            writer.write("<body>" + endl);
            
            writeBuildsTable(branchesPriv, privStatuses, writer);

            writeBuildsTable(branchesPub, statuses, writer);

            writer.write("</body>" + endl);
            writer.write("</html>" + endl);

        }

    }

    private static void header(FileWriter writer) throws IOException {
        writer.write("<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "  <title>Ignite Teamcity Build status</title>\n" +
            "  <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css\">\n" +
            "  <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/resources/demos/style.css\">\n" +
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
        Map<String, BuildStatusInBranches> statuses,
        FileWriter writer) throws IOException {


        String endl = String.format("%n");
        writer.write("<table>" + endl);

        writer.write("<tr>\n");
        writer.write("   <th>");
        writer.write("Build");
        writer.write("</th>\n");

        for (Branch next : branchesPriv) {
            writer.write("   <th>" + next.displayName + "</th>\n");
        }
        writer.write(" </tr>");

        Set<Map.Entry<String, BuildStatusInBranches>> entries = statuses.entrySet();
        for (Map.Entry<String, BuildStatusInBranches> next : entries) {
            writer.write("<tr>" + endl);
            writer.write("<td>" + endl);
            writer.write(next.getKey());
            writer.write("</td>" + endl);

            BuildStatusInBranches branches1 = next.getValue();
            for (Map.Entry<String, BuildStatusHref> entry : branches1.branchIdToStatusUrl.entrySet()) {
                writer.write("<td>" + endl);
                BuildStatusHref statusHref = entry.getValue();
                writer.write("<a href='" + statusHref.hrefToBuild + "'/>" + endl);
                writer.write("<img src='" + statusHref.statusImageSrc + "'/>" + endl);
                writer.write("</a>" + endl);
                writer.write("</td>" + endl);
            }

            writer.write("</tr>" + endl);
        }
        writer.write("</table>" + endl);
    }

    private static Map<String, BuildStatusInBranches> getBuildStatuses(
        final String tcId,
        final String projectId,
        final List<Branch> branchesPriv) throws Exception {

        final Map<String, BuildStatusInBranches> buildIdToStatus = new TreeMap<>();
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
                    System.out.println(url);

                    //_Ignite20Tests
                    BuildStatusInBranches statusInBranches = buildIdToStatus.computeIfAbsent(buildType.getName(), k -> new BuildStatusInBranches());
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
        return buildIdToStatus;
    }
}
