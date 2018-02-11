package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailure;

import static java.lang.Float.parseFloat;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.apache.ignite.internal.util.lang.GridFunc.isEmpty;

/**
 * Created by Дмитрий on 10.02.2018.
 */
@Path("chainResults")
public class GetChainResultsAsHtml {

    @Context
    private ServletContext context;




    //test here http://localhost:8080/rest/chainResults/html?serverId=public&buildId=1086222
    public void showChainOnServersResults(StringBuilder res, Integer buildId, String serverId) {
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);


        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, serverId)) {
            //processChainByRef(teamcity, includeLatestRebuild, build, true, true)
            String hrefById = teamcity.getBuildHrefById(buildId);
            BuildRef build = new BuildRef();
            build.href = hrefById;
            Optional<FullChainRunCtx> ctx =
                BuildChainProcessor.processChainByRef(teamcity, false, build,
                    true, false, false);

            ctx.ifPresent(c -> {
                ChainAtServerCurrentStatus status = new ChainAtServerCurrentStatus();

                status.chainName = c.suiteName();

                status.initFromContext(teamcity, c,
                    teamcity.runTestAnalysis(),
                    teamcity.runSuiteAnalysis());

                res.append(showChainAtServerData(status));
            });
        }
    }

    @GET
    @Produces(TEXT_HTML)
    @Path("html")
    public String getChainRes(@QueryParam("serverId") String serverId, @QueryParam("buildId") Integer buildId) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("<head>\n");
        builder.append("    <title>Ignite Teamcity - current failures</title>");
        builder.append(" <script src=\"https://code.jquery.com/jquery-1.12.4.js\"></script>\n");
        builder.append("    <script src=\"https://code.jquery.com/ui/1.12.1/jquery-ui.js\"></script>\n");
        builder.append("\n");
        builder.append("    <link rel=\"icon\" href=\"https://pbs.twimg.com/profile_images/568493154500747264/xTBxO73F.png\">\n");
        builder.append("    <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css\">");

        builder.append("<style>");
        builder.append("body {\n");
        builder.append(" \tfont-family: Arial;\n");
        builder.append(" \tfont-size: 12px;\n");
        builder.append(" \tfont-style: normal;\n");
        builder.append(" \tfont-variant: normal;\n");
        builder.append(" \tfont-weight: 500;\n");
        builder.append("}");
        builder.append("</style>");

        builder.append("</head>");
        builder.append("<body>\n");
        builder.append("<script>\n");
        builder.append("$(document).ready(function() {\n");
        builder.append("    $( document ).tooltip();\n");
        builder.append("     \n");
        builder.append("}); \n");
        builder.append("</script>\n");
        builder.append("\n");
        showChainOnServersResults(builder, buildId, serverId)      ;

        builder.append("\n");
        builder.append("</body>\n");
        builder.append("</html>");

        return builder.toString();
    }



    private boolean isDefinedAndFilled(Integer failures) {
        return failures!=null;
    }

    private String showChainAtServerData(ChainAtServerCurrentStatus server) {
        String res = "";
        String altTxt = "";

        if(!isEmpty(server.durationPrintable))
            altTxt += "duration: " + server.durationPrintable;

        res += "<b><a href='" + server.webToHist + "'>" + Strings.nullToEmpty(server.serverName);

        if (isDefinedAndFilled(server.chainName)) {
            res += " " + server.chainName;
        }

        res += "</a> ";
        res += "[";
        res += " <a href='" + server.webToBuild + "' title='" + altTxt + "'>";
        res += "tests " + server.failedTests + " suites " + server.failedToFinish + "";
        res += " </a>";
        res += "]";
        res += "</b><br><br>";

        StringBuilder resBuilder = new StringBuilder(res);
        for (SuiteCurrentStatus suite : server.suites) {
            resBuilder.append(showSuiteData(suite));
        }
        res = resBuilder.toString();

        return res;
    }

    private String showSuiteData(SuiteCurrentStatus suite) {
        String res = "";
        String altTxt = "duration: " + suite.durationPrintable;
        res += "&nbsp; ";

        String failRateText = "";
        if (isDefinedAndFilled(suite.failures) && isDefinedAndFilled(suite.runs) && isDefinedAndFilled(suite.failureRate)) {
            altTxt += "; " + suite.failures + " fails / " + suite.runs + " runs in all tracked branches in helper DB";
            failRateText += "(fail rate " + suite.failureRate + "%)";
            altTxt += "; " + failRateText;
        }
        String color = failureRateToColor(suite.failureRate);
        res += " <span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='" + failRateText + "'></span> ";

        res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
            "[ " + "<a href='" + suite.webToBuild + "' title='" + altTxt + "'> " + "tests " + suite.failedTests + " " + suite.result + "</a> ]";

        if (isDefinedAndFilled(suite.contactPerson))
            res += " " + suite.contactPerson + "";

        if(isDefinedAndFilled(suite.runningBuildCount) && suite.runningBuildCount!=0) {
            res+=" <img src='https://image.flaticon.com/icons/png/128/2/2745.png' width=12px height=12px> ";
            res+=" " + suite.runningBuildCount + " running";
        }
        if(isDefinedAndFilled(suite.queuedBuildCount) && suite.queuedBuildCount!=0) {
            res+=" <img src='https://d30y9cdsu7xlg0.cloudfront.net/png/273613-200.png' width=12px height=12px> ";
            res+="" + suite.queuedBuildCount + " queued";
        }

        /* //no triggering support in static version
        if(isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
            res+=" <a href='javascript:void(0);' ";
            res+=" onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \""+suite.branchName+"\")' ";
            res+=" title='trigger build'";
            res+=" >run</a> ";
        }
        */
        res+=" <br>";

        List<TestFailure> failures = suite.testFailures;
        StringBuilder resBuilder = new StringBuilder(res);
        for (TestFailure next : failures) {
            resBuilder.append(showTestFailData(next));
        }
        res = resBuilder.toString();
        
        if(isDefinedAndFilled(suite.webUrlThreadDump)) {
            res += "&nbsp; &nbsp; <a href='" + suite.webUrlThreadDump + "'>";
            res += "<img src='https://cdn2.iconfinder.com/data/icons/metro-uinvert-dock/256/Services.png' width=12px height=12px> ";
            res += "Thread Dump</a>";
            res += " <br>";
        }

        res += " <br>";
        return res;
    }

    private boolean isDefinedAndFilled(String id) {
        return !Strings.isNullOrEmpty(id);
    }


    private String showTestFailData(TestFailure testFail) {
        String res = "";
        res += "&nbsp; &nbsp; ";

        boolean haveIssue = isDefinedAndFilled(testFail.webIssueUrl) && isDefinedAndFilled(testFail.webIssueText);

        String color = failureRateToColor(testFail.failureRate);
        res += " <span style='background-color: " + color + "; width:7px; height:7px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";

        if (haveIssue) {
            res += "<a href='" + testFail.webIssueUrl + "'>";
            res += testFail.webIssueText;
            res += "</a>";
            res += ": ";
        }
        ;

        res += testFail.name;

        boolean haveWeb = isDefinedAndFilled(testFail.webUrl);
        String histContent = "";
        if (testFail.failures != null && testFail.runs != null) {
            histContent += " <span title='" + testFail.failures + " fails / " + testFail.runs + " runs in all tracked branches in helper DB'>";
            if (isDefinedAndFilled(testFail.failureRate))
                histContent += "(fail rate " + testFail.failureRate + "%)";
            else
                histContent += "(fails: " + testFail.failures + "/" + testFail.runs + ")";
            histContent += "</span>";
        }
        else if (haveWeb) {
            histContent += " (test history)";
        }
        if (haveWeb)
            res += "<a href='" + testFail.webUrl + "'>";
        res += histContent;
        if (haveWeb)
            res += "</a>";

        res += " <br>";
        return res;
    }


    String failureRateToColor(String failureRate) {
        float redSaturation = 255;
        float greenSaturation = 0;
        float blueSaturation = 0;

        float colorCorrect = 0;
        if (isDefinedAndFilled(failureRate)) {
            colorCorrect = parseFloat(failureRate);
        }

        if (colorCorrect < 50) {
            redSaturation = 255;
            greenSaturation += colorCorrect * 5;
        }
        else {
            greenSaturation = 255 - (colorCorrect - 50) * 5;
            redSaturation = 255 - (colorCorrect - 50) * 5;
        }
        return rgbToHex(redSaturation, greenSaturation, blueSaturation);
    }

    String componentToHex(float c) {
        int cInt = (int)c;
        String cStr = Integer.toHexString(cInt > 255 ? 255 : cInt);
        String s = Strings.padStart(cStr, 2, '0');
        return s;
    }

    String rgbToHex(float r, float g, float b) {
        return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
    }
}
