/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.runners;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IgniteTeamcityConnection;
import org.apache.ignite.ci.tcmodel.conf.BuildType;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.util.UrlUtil.escapeOrB64;

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
        File file = new File("./status.html");
        try (FileWriter writer = new FileWriter(file)) {
            generate(writer);
        }
        System.out.println("Page was saved to " + file.getAbsolutePath());
    }

    public static void generate(Writer writer) throws Exception {
        boolean groupByResponsible = true;
        boolean includeTabAll = groupByResponsible && true;
        final String verComments = "Version 10. Responsibilities loaded from config";
        final List<Branch> branchesPriv = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            //new Branch("ignite-2.1.4", "ignite-2.1.4", "ignite-2.1.4"),
            new Branch("ignite-2.1.5", "ignite-2.1.5", "ignite-2.1.5"));
        final String tcPrivId = "private";
        final String privProjectId = "id8xIgniteGridGainTests";
        final ProjectStatus privStatuses = getBuildStatuses(tcPrivId, privProjectId, branchesPriv);

        final List<Branch> branchesPub = Lists.newArrayList(
            new Branch("", "<default>", "master"),
            new Branch(
                "pull/2508/head",
                "pull/2508/head", "ignite-2.1.5"),

            new Branch(
                "ignite-2.3",
                "ignite-2.3", "ignite-2.3")
        );
        final String pubTcId = "public";
        final String projectId = "Ignite20Tests";
        ProjectStatus pubStatus = getBuildStatuses(pubTcId, projectId, branchesPub);

        TreeSet<String> respPersons;
        Properties privResp;
        Properties pubResp;
        if (groupByResponsible) {
            privResp = HelperConfig.loadContactPersons(tcPrivId);
            pubResp = HelperConfig.loadContactPersons(pubTcId);

            respPersons = allRespPersons(privResp, pubResp);
            System.err.println(respPersons);
        }
        else {
            respPersons = Sets.newTreeSet();
            respPersons.add("all");
            pubResp = null;
            privResp = null;
        }

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

        line(writer, "<p>" +
            verComments +
            "</p>");
        line(writer, "</body>");
        line(writer, "</html>");
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

    private static void line(Writer writer, String str) throws IOException {
        writer.write(str + ENDL);
    }


    private static TreeSet<String> allRespPersons(Map privResp, Map pubResp) {
        TreeSet<String> respPerson = new TreeSet<>();

        for (Object next : privResp.values())
            respPerson.add(Objects.toString(next));

        for (Object next : pubResp.values())
            respPerson.add(Objects.toString(next));

        return respPerson;
    }

    private static void header(Writer writer, boolean groupByResponsible) throws IOException {
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

        HtmlBuilder tbl = writer.start("table");
        final HtmlBuilder hdr = tbl.start("tr");
        hdr.start("th").line("Build").end("th");
        for (Branch next : branches) {
            hdr.start("th").line(next.displayName).end("th");
        }
        hdr.end("tr");

        for (Map.Entry<String, SuiteStatus> suiteStatusEntry : projectStatus.suiteIdToStatusUrl.entrySet()) {
            if(!includeBuildId.test(suiteStatusEntry.getValue().suiteId))
                continue;

            final HtmlBuilder row = tbl.start("tr");

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
        tbl.end("table");
    }

    private static ProjectStatus getBuildStatuses(
        final String tcId,
        final String projectId,
        final List<Branch> branchesPriv) throws Exception {

        ProjectStatus projStatus = new ProjectStatus();
        IgniteTeamcityConnection teamcityHelper = new IgniteTeamcityConnection(tcId);

        List<BuildType> suites = teamcityHelper.getProjectSuites(projectId).get();

        for (BuildType buildType : suites) {
            if (!"-> Run All".equals(buildType.getName())
                && buildType.getName().startsWith("->"))
                continue;

            for (Branch branch : branchesPriv) {
                String branchIdRest = escapeOrB64(branch.idForRest);
                String branchIdUrl = escape(branch.idForUrl);
                String imgSrc = teamcityHelper.host() +
                    "app/rest/builds/" +
                    "buildType:(id:" +
                    buildType.getId() +
                    ")" +
                    (isNullOrEmpty(branchIdRest) ? "" :
                        (",branch:(name:" + branchIdRest + ")")) +
                    "/statusIcon.svg";

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

        return projStatus;
    }

    public static class HtmlBuilder {

        private final String prefix;
        private int spacing;
        private Writer writer;

        public HtmlBuilder(Writer writer, int spacing) {
            this.writer = writer;
            prefix = Strings.repeat(" ", spacing);
            this.spacing = spacing;
        }

        public HtmlBuilder(Writer writer) {
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
