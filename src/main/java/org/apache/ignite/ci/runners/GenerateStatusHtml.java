package org.apache.ignite.ci.runners;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
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
                 "pull/2296/head",
                //"ignite-2.1.3",
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



            HtmlBuilder builder = new HtmlBuilder(writer);
            builder.line("<div id=\"tabs\">");

            HtmlBuilder list = builder.start("ul");
            for (String resp : respPersons) {
                String dispId = getDivId(resp);
                list.line("<li><a href=\"#" + dispId + "\">" + dispId + "</a></li>");
            }
            builder.end("ul");
            
            for (String curResponsiblePerson : respPersons) {
                builder.line("<div id='" + getDivId(curResponsiblePerson) + "'>");

                builder.line("Private TC status");
                writeBuildsTable(branchesPriv, privStatuses, builder,
                    buildId -> isPropertyValueEquals(privResp, buildId, curResponsiblePerson));

                builder.line("<br><br>Public TC status");
                writeBuildsTable(branchesPub, pubStatus, builder,
                    buildId -> isPropertyValueEquals(pubResp, buildId, curResponsiblePerson));

                builder.line("</div>");
            }
            line(writer, "</div>");
            line(writer, "</body>");
            line(writer, "</html>");
        }
    }

    private static boolean isPropertyValueEquals(Properties privResp, String code, String valueExpected) {

        String normalize = normalize(code);
        return valueExpected.equals(normalize(privResp.getProperty(normalize)));
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
            "  <style>\n" +
                "        td {\n" +
                "         font-size: 10pt;\n" +
                "        }\n" +
                "    </style>" +
            "</head>");
    }

    private static void writeBuildsTable(
        final List<Branch> branches,
        final ProjectStatus projectStatus,
        final HtmlBuilder writer,
        final Predicate<String> includeBuildId) {

        HtmlBuilder table = writer.start("table");
        final HtmlBuilder header = table.start("tr");
        header.start("th").line("Build").end("th");
        for (Branch next : branches) {
            header.start("th").line(next.displayName).end("th");
        }
        header.end("tr");

        for (Map.Entry<String, SuiteStatus> suiteStatusEntry : projectStatus.suiteIdToStatusUrl.entrySet()) {
            if(!includeBuildId.test(suiteStatusEntry.getValue().suiteId))
                continue;

            final HtmlBuilder row = table.start("tr");

            row.start("td").line(suiteStatusEntry.getKey()).end("td");

            SuiteStatus branches1 = suiteStatusEntry.getValue();

            for (Branch branch : branches) {
                BuildStatusHref statusHref = branches1.branchIdToStatusUrl.get(branch.idForRest);
                HtmlBuilder cell = row.start("td");
                cell.line("<a href='" + statusHref.hrefToBuild + "'>" );
                cell.line("<img src='" + statusHref.statusImageSrc + "'/>");
                cell.line("</a>");
                cell.end("td");
            }
            row.end("tr");
        }
        table.end("table");
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

    public static class HtmlBuilder {

        private final String prefix;
        private int spacing;
        private FileWriter writer;

        public HtmlBuilder(FileWriter writer, int spacing) {
            this.writer = writer;
            prefix = Strings.repeat(" ", spacing);
            this.spacing = spacing;
        }

        public HtmlBuilder(FileWriter writer) {
            this(writer, 0);
        }

        public HtmlBuilder line(String str) {
            write(prefix + str + ENDL);
            return this;
        }

        public void write(String str1) {
            try {
                writer.write(str1);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private HtmlBuilder start(String tag) {
            String text = "<" + tag + ">";
            line(text);
            return new HtmlBuilder(writer, spacing + 4);
        }

        private HtmlBuilder end(String tag) {
            int newSpacing = this.spacing - 4;
            HtmlBuilder builder = new HtmlBuilder(writer, newSpacing > 0 ? newSpacing : 0);
            builder.line("</" + tag + ">");
            return builder;
        }

        public HtmlBuilder text(String txt) {
            write(txt);
            return this;
        }
    }
}
