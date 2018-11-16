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

package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.google.inject.Injector;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.hist.TestHistory;

import static java.lang.Float.parseFloat;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.apache.ignite.internal.util.lang.GridFunc.isEmpty;

/**
 *
 */
@Path("chainResults")
public class GetChainResultsAsHtml {
    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;
    
    //test here http://localhost:8080/rest/chainResults/html?serverId=public&buildId=1086222
    public void showChainOnServersResults(StringBuilder res, Integer buildId, String srvId) {
        //todo solve report auth problem
        final Injector injector = CtxListener.getInjector(ctx);
        final BuildChainProcessor buildChainProcessor = injector.getInstance(BuildChainProcessor.class);

        String failRateBranch = ITeamcity.DEFAULT;

        ITcServerProvider tcHelper = injector.getInstance(ITcServerProvider.class);
        final ICredentialsProv creds = ICredentialsProv.get(req);
        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(srvId, creds);
        ITeamcityIgnited teamcityIgnited = injector.getInstance(ITeamcityIgnitedProvider.class).server(srvId, creds);

        final FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(teamcity, teamcityIgnited, Collections.singletonList(buildId),
            LatestRebuildMode.NONE,
            ProcessLogsMode.SUITE_NOT_COMPLETE, false,
            failRateBranch);

        ChainAtServerCurrentStatus status = new ChainAtServerCurrentStatus(teamcity.serverId(), ctx.branchName());

        ctx.getRunningUpdates().forEach(FutureUtil::getResultSilent);

        status.chainName = ctx.suiteName();

        status.initFromContext(teamcity, ctx, teamcity, failRateBranch);

        res.append(showChainAtServerData(status));

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



    private boolean isDefinedAndFilled(Object failures) {
        return failures!=null;
    }

    private String showChainAtServerData(ChainAtServerCurrentStatus server) {
        String res = "";
        String altTxt = "";

        if(!isEmpty(server.durationPrintable))
            altTxt += "duration: " + server.durationPrintable;

        res += "<b><a href='" + server.webToHist + "'>" + Strings.nullToEmpty(server.serverId);

        if (isDefinedAndFilled(server.chainName))
            res += " " + server.chainName;

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
        for (TestFailure next : failures)
            resBuilder.append(showTestFailData(next));
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

        String color = (isDefinedAndFilled(testFail.histBaseBranch) && isDefinedAndFilled(testFail.histBaseBranch.recent))
            ? failureRateToColor(testFail.histBaseBranch.recent.failureRate)
            : "white";

        boolean investigated = testFail.investigated;
        if(investigated) {
            res += "<img src='https://d30y9cdsu7xlg0.cloudfront.net/png/324212-200.png' width=8px height=8px> ";
            res += "<span style='opacity: 0.75'> ";
        }

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

        TestHistory hist;

        if(isDefinedAndFilled(testFail.histBaseBranch))
            hist = testFail.histBaseBranch;
        else
            hist = null;

        if (hist!=null) {
            String testFailTitle = "";

            if(isDefinedAndFilled(hist.recent))
                testFailTitle = "recent rate: " + hist.recent.failures + " fails / " + hist.recent.runs + " runs" ;

            if(isDefinedAndFilled(hist.allTime) && isDefinedAndFilled(hist.allTime.failures)) {
                testFailTitle +=
                    "; all history: " + hist.allTime.failureRate + "% ["+
                        hist.allTime.failures + " fails / " +
                        hist.allTime.runs + " runs] " ;
            }

            histContent += " <span title='" +testFailTitle + "'>";

            if (isDefinedAndFilled(hist.recent) && isDefinedAndFilled(hist.recent.failureRate))
                histContent += "(fail rate " + hist.recent.failureRate + "%)";
            else
                histContent += "(fails: " + hist.recent.failures + "/" + hist.recent.runs + ")";

            if(isDefinedAndFilled(testFail.histCurBranch)) {
                //todo presence of this indicates that PR is checked, need to draw latest
            }

            histContent += "</span>";
        } else if (haveWeb)
            histContent += " (test history)";

        if (haveWeb)
            res += "<a href='" + testFail.webUrl + "'>";
        res += histContent;
        if (haveWeb)
            res += "</a>";


        if(investigated)
            res += "</span> ";

        res += " <br>";
        return res;
    }


    private String failureRateToColor(String failureRate) {
        float redSaturation = 255;
        float greenSaturation = 0;
        float blueSaturation = 0;

        float colorCorrect = 0;
        if (isDefinedAndFilled(failureRate))
            colorCorrect = parseFloat(failureRate.replace(",", "."));

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

    private String componentToHex(float c) {
        int cInt = (int)c;
        String cStr = Integer.toHexString(cInt > 255 ? 255 : cInt);
        return Strings.padStart(cStr, 2, '0');
    }

    private String rgbToHex(float r, float g, float b) {
        return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
    }
}
