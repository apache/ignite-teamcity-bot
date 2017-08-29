package org.apache.ignite.ci.runners;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.util.Base64Util;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Generates html with status builds
 */
public class GenerateStatusHtml {
    private static final String ENDL = String.format("%n");
    private static final String ENC = "UTF-8";

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
        boolean groupByResponsible = true;
        boolean includeTabAll = groupByResponsible && true;
        final List<Branch> branchesPriv = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch("ignite-2.1.4", "ignite-2.1.4", "ignite-2.1.4"),
            new Branch("ignite-2.1.5", "ignite-2.1.5", "ignite-2.1.5"));
        final String tcPrivId = "private";
        final String privProjectId = "id8xIgniteGridGainTests";
        final ProjectStatus privStatuses = getBuildStatuses(tcPrivId, privProjectId, branchesPriv);

        final List<Branch> branchesPub = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch(
                "pull/2400/head",
                "pull/2400/head", "make-teamсity-green-again"),
            new Branch(
                "pull/2380/head",
                "pull/2380/head", "ignite-2.1.4"),
            new Branch(
                "pull/2508/head",
                "pull/2508/head", "ignite-2.1.5"));
        final String pubTcId = "public";
        final String projectId = "Ignite20Tests";
        ProjectStatus pubStatus = getBuildStatuses(pubTcId, projectId, branchesPub);

        TreeSet<String> respPersons;
        Properties privResp;
        Properties pubResp;
        if (groupByResponsible) {
            privResp = HelperConfig.loadPrefixedProperties(tcPrivId, HelperConfig.RESP_FILE_NAME);
            pubResp = HelperConfig.loadPrefixedProperties(pubTcId, HelperConfig.RESP_FILE_NAME);

            respPersons = allRespPersons(privResp, pubResp);
            System.err.println(respPersons);
        }
        else {
            respPersons = Sets.newTreeSet();
            respPersons.add("all");
            pubResp = null;
            privResp = null;
        }

        final String fileName = "./ignite-tc-helper-web/src/main/webapp/status/index" + (groupByResponsible ? "" : "_all") + ".html";
        File file = new File("./status.html");
        try (FileWriter writer = new FileWriter(file)) {
            line(writer, "<html>");
            header(writer, groupByResponsible);
            line(writer, "<body>");

            HtmlBuilder builder = new HtmlBuilder(writer);

            String tabAllId = "all";
            Iterable<String> tabs = includeTabAll ? Iterables.concat(Lists.newArrayList(tabAllId), respPersons) : respPersons;
            if (groupByResponsible) {
                builder.line("<div id=\"tabs\">");

                HtmlBuilder list = builder.start("ul");
                for (String resp : tabs) {
                    String dispId = getDivId(resp);
                    list.line("<li><a href=\"#" + dispId + "\">" + dispId + "</a></li>");
                }
                builder.end("ul");
            }

            boolean isFirst = true;
            for (String curResponsiblePerson : tabs) {
                builder.line("<div id='" + getDivId(curResponsiblePerson) + "'>");

                builder.line("Private TC status");
                boolean includeAll = !groupByResponsible || (isFirst & includeTabAll);
                writeBuildsTable(branchesPriv, privStatuses, builder,
                    buildId -> includeAll || isPropertyValueEquals(privResp, buildId, curResponsiblePerson));

                builder.line("<br><br>Public TC status");
                writeBuildsTable(branchesPub, pubStatus, builder,
                    buildId -> includeAll || isPropertyValueEquals(pubResp, buildId, curResponsiblePerson));

                builder.line("</div>");

                isFirst = false;
            }
            line(writer, "</div>");

            line(writer, "<p>Version 5. New branch MCGA added</p>");
            line(writer, "</body>");
            line(writer, "</html>");
        }
        System.out.println("Page was saved to " + file.getAbsolutePath());
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

    private static void header(FileWriter writer, boolean groupByResponsible) throws IOException {
        writer.write("<head>\n" +
            "  <meta charset=\"utf-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "  <title>Ignite Teamcity Build status</title>\n" +
            "  <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css\">\n" +
            "  <script src=\"https://code.jquery.com/jquery-1.12.4.js\"></script>\n" +
            "  <script src=\"https://code.jquery.com/ui/1.12.1/jquery-ui.js\"></script>\n");
        if(groupByResponsible) {
            writer.write("  <script>\n");

            writer.write("$( function() {\n" +
                "    var index = 'tcStatus_lastOpenedTab';  //  Define friendly index name\n" +
                "    var dataStore = window.sessionStorage;  //  Define friendly data store name\n" +
                "    try {\n" +
                "        // getter: Fetch previous value\n" +
                "        var oldIndex = dataStore.getItem(index);\n" +
                "    } catch(e) {\n" +
                "        // getter: Always default to first tab in error state\n" +
                "        var oldIndex = 0;\n" +
                "    }\n" +
                "    $('#tabs').tabs({\n" +
                "        // The zero-based index of the panel that is active (open)\n" +
                "        active : oldIndex,\n" +
                "        // Triggered after a tab has been activated\n" +
                "        activate : function( event, ui ){\n" +
                "            //  Get future value\n" +
                "            var newIndex = ui.newTab.parent().children().index(ui.newTab);\n" +
                "            //  Set future value\n" +
                "            dataStore.setItem( index, newIndex )\n" +
                "        }\n" +
                "    });\n" +
                "  });");
            writer.write("  </script>\n");
        }
        writer.write(
            "  <style>\n" +
                "        td {\n" +
                "         font-size: 10pt;\n" +
                "        }\n" +
                "table tr:nth-child(odd) td{\n" +
                "\n" +
                "  background-color: #EEEEEE;" +
                "}\n" +
                "table tr:nth-child(even) td{\n" +
                "}" +
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
                if (buildType.getName().startsWith("->"))
                    continue;

                for (Branch branch : branchesPriv) {
                    String branchIdRest = URLEncoder.encode(branch.idForRest, ENC);
                    String branchIdUrl = URLEncoder.encode(branch.idForUrl, ENC);

                    if(Strings.nullToEmpty(branch.idForRest).contains("/")) {
                        String idForRestEncoded = Base64Util.encodeUtf8String(branch.idForRest);
                        branchIdRest = "($base64:" + idForRestEncoded + ")";
                    }

                    String imgSrc = teamcityHelper.host() +
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
                        new BuildStatusHref(imgSrc, href));
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
