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
//loadData(); // should be defined by page
//loadStatus element should be provided on page
//triggerConfirm & triggerDialog element should be provided on page (may be hidden)
var g_initMoreInfoDone = false;

/** Object used to notify git. See ChainAtServerCurrentStatus Java class. */
var g_srv_to_notify_git;

//@param results - TestFailuresSummary
function showChainOnServersResults(result) {
    var minFailRateP = findGetParameter("minFailRate");
    var minFailRate = minFailRateP == null ? 0 : parseFloat(minFailRateP);

    var maxFailRateP = findGetParameter("maxFailRate");
    var maxFailRate = maxFailRateP == null ? 100 : parseFloat(maxFailRateP);

    var hideFlakyFailuresP = findGetParameter("hideFlakyFailures");
    var hideFlakyFailures = hideFlakyFailuresP == null ? false : "true"===hideFlakyFailuresP;

    return showChainResultsWithSettings(result, new Settings(minFailRate, maxFailRate, result.javaFlags, hideFlakyFailures));
}

class Settings {
    constructor(minFailRate, maxFailRate, javaFlags, hideFlakyFailures) {
        this.minFailRate = minFailRate;
        this.maxFailRate = maxFailRate;
        this.javaFlags = javaFlags;
        this.hideFlakyFailures = hideFlakyFailures;
    }

    isTeamCityAvailable() {
        return this.javaFlags & 1;
    };

    isGithubAvailable() {
        return this.javaFlags & 2
    };

    isJiraAvailable() {
        return this.javaFlags & 4
    };
}

//@param results - TestFailuresSummary
//@param settings - Settings (JS class)
function showChainResultsWithSettings(result, settings) {
    var res = "";
    res += "<table border='0px'><tr><td colspan='4'>Chain results";

    if(isDefinedAndFilled(result.trackedBranch)) {
        res+=" for [" + result.trackedBranch + "]";
    }

    if (isDefinedAndFilled(result.failedTests) &&
        isDefinedAndFilled(result.failedToFinish)) {
        res += " [";
        res += "tests " + result.failedTests + " suites " + result.failedToFinish + "";
        res += "]";
    } else
        res += " is absent";

    res += "</td></tr>";
    res += "</table></br>";

    for (var i = 0; i < result.servers.length; i++) {
        var server = result.servers[i];
        res += showChainCurrentStatusData(server, settings);
    }

    res += "<tr bgcolor='#F5F5FF'><th colspan='4' class='table-title'><b>New Tests</b></th></tr>"

    for (var i = 0; i < result.servers.length; i++) {
        var newTests = result.servers[i].newTestsUi;
        res += showNewTestsData(newTests, settings);
    }

    res += "<tr><td colspan='4'>&nbsp;</td></tr>";
    res += "</table>";

    setTimeout(initMoreInfo, 100);

    return res;
}

/**
 * @param chain - see org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus Java Class.
 * @param settings - see Settings JavaScript class.
 */
function showNewTestsData(chain, settings) {
    var res = "";

    newTestRows = "";

    res += "<table style='width:100%'>";

    for (var i = 0; i < chain.length; i++) {
        var newTests = chain[i].tests;
        newTestRows += "<tr><td colspan='2' width='10%'></td>";
        newTestRows += "<td colspan='2' width='80%'><a href='" + chain[i].webToBuild + "'>" + chain[i].name + "</a>" + "</td></tr>";
        newTestRows += "<td colspan='2' width='10%'></td>";
        for (var j = 0; j < newTests.length; j++) {
            newTestsFounded = true
            var newTest = newTests[j];
            testColor = newTest.status ? "#013220" : "#8b0000";
            newTestRows += "<tr style='color:" + testColor + "'>";
            newTestRows += "<td colspan='2' width='10%'></td>";
            newTestRows += "<td width='5%'>" + (newTest.status ? "PASSED" : "FAILED") + "</td>";
            if (isDefinedAndFilled(newTest.suiteName) && isDefinedAndFilled(newTest.testName))
                newTestRows += "<td width='75%'>" + newTest.suiteName + ": " + newTest.testName + "</td>";
            else
                newTestRows += "<td width='75%'>" + newTest.name + "</td>";
            newTestRows += "<td colspan='2' width='10%'></td>";
            newTestRows += "</tr>";
        }
    }

    res += newTestRows !== "" ? newTestRows : "<tr><td colspan='2' width='10%'></td><td width='90%'>No new tests</td></tr>"

    res += "</table>";

    return res;

}

/**
 * @param chain - see org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus Java Class.
 * @param settings - see Settings JavaScript class.
 */
function showChainCurrentStatusData(chain, settings) {
    if(!isDefinedAndFilled(chain))
        return;

    if(isDefinedAndFilled(chain.buildNotFound) && chain.buildNotFound ) {
        return "<tr><td><b>Error: Build not found for branch [" + chain.branchName + "]</b>" +
            "<br><br><span style='color:grey; font-size:12px;'>Perhaps, more than 2 weeks have passed since the last build " +
            "run. <br>There is no data on the TC server</span></td></tr>";
    }

    var res = "";

    res += "<table style='width: 100%;' border='0px'>";
    res += "<tr bgcolor='#F5F5FF'><td colspan='3' width='75%'>";
    res += "<table style='width: 40%'>";
    res += "<tr><td><b> Server: </b></td><td>[" + chain.serverCode +"] TC: ["+  chain.tcServerCode + "]</td></tr>";

    if (isDefinedAndFilled(chain.prNum)) {
        res += "<tr><td><b> PR: </b></td><td>";

        if (isDefinedAndFilled(chain.webToPr))
            res += "<a href='" + chain.webToPr + "'>[#" + chain.prNum + "]</a>";
        else
            res += "[#" + chain.prNum + "]";

        res += "</td></tr>";
    }

    if (isDefinedAndFilled(chain.webToTicket) && isDefinedAndFilled(chain.ticketFullName)) {
        res += "<tr><td><b> Ticket: </b></td><td>";
        res += "<a href='" + chain.webToTicket + "'>[" + chain.ticketFullName + "]</a>";
        res += "</td></tr>";
    }

    let parentSuitId;

    if (isDefinedAndFilled(findGetParameter("suiteId")))
        parentSuitId = findGetParameter("suiteId");
    else if (isDefinedAndFilled(chain.suiteId))
        parentSuitId = chain.suiteId;

    if (isDefinedAndFilled(parentSuitId) || isDefinedAndFilled(chain.webToHist)) {
        res += "<tr><td>";
        if (isDefinedAndFilled(parentSuitId)) {
            res += "<b> Suite: </b></td>" +
                "<td>[" + parentSuitId + "] ";
        }
        if (isDefinedAndFilled(chain.webToHist)) {
            res += " <a href='" + chain.webToHist + "' title='Chain history'>[TC history]</a>";
        }
        if (isDefinedAndFilled(chain.webToBuild)) {
            res += " <a href='" + chain.webToBuild + "' title='Build without applying re-runs'>[Build]</a>";
        }
        res += "</td></tr>";
    }

    res += "</table>";
    res += "</br>";

    if (isDefinedAndFilled(chain.chainName)) {
        res += chain.chainName + " ";
    }

    res += "<b>Chain result: </b>";

    if (isDefinedAndFilled(chain.failedToFinish) && isDefinedAndFilled(chain.failedTests))
        res += chain.failedToFinish + " suites and " + chain.failedTests + " tests failed";
    else
        res += "empty";

    res += " ";

    var moreInfoTxt = "";

    var cntFailed = 0;
    var suitesFailedList = "";
    for (var i = 0; i < chain.suites.length; i++) {
        var suite = chain.suites[i];

        if (!isDefinedAndFilled(suite.suiteId))
            continue;

        //may check failure here in case mode show all

        if (suitesFailedList.length !== 0)
            suitesFailedList += ",";

        suitesFailedList += suite.suiteId;
        cntFailed++;
    }

    //chain.tcServerCode can represent reference to a service generated using alias.
    let srvCodeForTriggering = chain.serverCode;

    if (suitesFailedList.length !== 0 && isDefinedAndFilled(srvCodeForTriggering) && isDefinedAndFilled(chain.branchName)) {
        moreInfoTxt += "Trigger failed " + cntFailed + " builds";
        moreInfoTxt += " <a href='javascript:void(0);' ";
        moreInfoTxt += " onClick='triggerBuilds(\"" + srvCodeForTriggering + "\", \"" + parentSuitId + "\", " +
            "\"" + suitesFailedList + "\", \"" + chain.branchName + "\", false, false, null, \"" + chain.prNum + "\", null, false)' ";
        moreInfoTxt += " title='trigger builds'>in queue</a> ";

        moreInfoTxt += " <a href='javascript:void(0);' ";
        moreInfoTxt += " onClick='triggerBuilds(\"" + srvCodeForTriggering + "\", \"" + parentSuitId + "\", " +
            "\"" + suitesFailedList + "\", \"" + chain.branchName + "\", true, false, null, \"" + chain.prNum + "\", null, false)' ";
        moreInfoTxt += " title='trigger builds'>on top</a><br>";
    }

    moreInfoTxt += "Duration: " + chain.durationPrintable + " " +
        "(Net Time: " + chain.durationNetTimePrintable + "," +
        " Tests: " + chain.testsDurationPrintable + "," +
        " Src. Update: " + chain.sourceUpdateDurationPrintable + "," +
        " Artifacts Publishing: " + chain.artifcactPublishingDurationPrintable + "," +
        " Dependecies Resolving: " + chain.dependeciesResolvingDurationPrintable + "," +
        " Timeouts: " + chain.lostInTimeouts + ")<br>";

    if(isDefinedAndFilled(chain.totalTests))
        moreInfoTxt += " <span title='Not muted and not ignored tests'>Total tests: " + chain.totalTests + "</span>";

    if(isDefinedAndFilled(chain.trustedTests))
        moreInfoTxt += " <span title='Tests which not filtered out because of flakyness'>Trusted tests: " + chain.trustedTests + "</span>";

    moreInfoTxt += "<br>";

    if (isDefinedAndFilled(chain.topLongRunning) && chain.topLongRunning.length > 0) {
        moreInfoTxt += "Top long running:<br>";

        moreInfoTxt += "<table>";
        for (var j = 0; j < chain.topLongRunning.length; j++) {
            moreInfoTxt += showTestFailData(chain.topLongRunning[j], false, settings);
        }
        moreInfoTxt += "</table>";
    }


    if (isDefinedAndFilled(chain.logConsumers) && chain.logConsumers.length > 0) {
        moreInfoTxt += "Top Log Consumers:<br>";

        moreInfoTxt += "<table>";
        for (var k = 0; k < chain.logConsumers.length; k++) {
            moreInfoTxt += showTestFailData(chain.logConsumers[k], false, settings);
        }
        moreInfoTxt += "</table>";
    }

    if(!isDefinedAndFilled(findGetParameter("reportMode"))) {
        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>" + more + "</a>";
        res += "<div class='content'>" + moreInfoTxt + "</div></span>";
    }

    res += "</td><td>";

    // if (settings.isGithubAvailable()) {
    //     g_srv_to_notify_git = server;
    //     res += "<button onclick='notifyGit()'>Update PR status</button>";
    // }

    let baseBranchForTc = chain.baseBranchForTc;
    if (settings.isJiraAvailable() && isDefinedAndFilled(srvCodeForTriggering)) {
        res += "<button onclick='commentJira(\"" + srvCodeForTriggering + "\", " +
            "\"" + chain.branchName + "\", " +
            "\"" + parentSuitId + "\", " +
            "\"\", " + // ticket id
            "\"" + baseBranchForTc + "\")'>Comment JIRA</button><br>";

        var blockersList = "";

        for (var l = 0; l < chain.suites.length; l++) {
            var suite0 = chain.suites[l];

            var suiteOrNull = filterPossibleBlocker(suite0);

            if (suiteOrNull != null) {
                if (blockersList.length !== 0)
                    blockersList += ",";

                blockersList += suite0.suiteId;
            }
        }

        res += "<label for='cleanRebuild'><input id='cleanRebuild' type='checkbox'>Delete all files in checkout directory before each snapshot dependency build</label><br>"

        res += "<button onclick='triggerBuilds(" +
            "\"" + srvCodeForTriggering + "\", " +
            "\"" + parentSuitId + "\", " +
            "\"" + blockersList + "\", " +
            "\"" + chain.branchName + "\", " +
            "false, " + //top
            "false, " + //observe
            "null, " + // ticketId
            "\"" + + chain.prNum + "\", " +
            "\"" + baseBranchForTc + "\", " +
            "document.getElementById(\"cleanRebuild\").checked" +
            ")'> " +
            "Re-run possible blockers</button><br>";

        res += "<button onclick='triggerBuilds(" +
            "\"" + srvCodeForTriggering + "\", " +
            "\"" + parentSuitId + "\", " +
            "\"" + blockersList + "\", " +
            "\"" + chain.branchName + "\", " +
            "true, " + //top
            "false, " + //observe
            "null, " + // ticketId
            "\"" + chain.prNum + "\", " + //prNum
            "\"" + baseBranchForTc + "\", " +
            "document.getElementById(\"cleanRebuild\").checked" +
            ")'> " +
            "Re-run possible blockers (top queue)</button><br>";
    }

    if (isDefinedAndFilled(baseBranchForTc)) {
        // if (settings.isGithubAvailable())
        //     res+="<br>";

        if (settings.isJiraAvailable())
            res += "<br>";

        res += "Base branch";
        res += ": " + baseBranchForTc.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    res += "&nbsp;</td></tr>";

    res += addBlockersData(chain, settings);

    for (var m = 0; m < chain.suites.length; m++) {
        var subSuite = chain.suites[m];

        res += showSuiteData(subSuite, settings, chain.prNum);
    }

    return res;
}

/**
 * Creates table with possible blockers.
 *
 * @param server - see ChainAtServerCurrentStatus Java class.
 * @param settings - see Settings JavaScript class.
 * @returns {string} Table rows with possible blockers and table headers.
 * Or empty string if no blockers found.
 */
function addBlockersData(server, settings) {
    if (findGetParameter("action") !== "Latest")
        return "";

    var blockers = "";

    for (var i = 0; i < server.suites.length; i++) {
        var suite = server.suites[i];

        suite = filterPossibleBlocker(suite);

        if (suite != null)
            blockers += showSuiteData(suite, settings, server.prNum);
    }

    if (blockers === "") {
        blockers = "<tr bgcolor='#D6F7C1'><th colspan='3' class='table-title'>" +
            "<b>Possible Blockers not found!</b></th>" +
            "<th class='table-title'>Base Branch</th></tr>";
    }
    else {
        let blockersHeader = "<tr bgcolor='#F5F5FF'><th colspan='3' class='table-title'><b>Possible Blockers";

        if (isDefinedAndFilled(server.totalBlockers))
            blockersHeader += " (" + server.totalBlockers + ")";

        blockersHeader += "</b></th>" +  "<th class='table-title'>Base Branch</th></tr>";

        blockers = blockersHeader + blockers;
    }

    blockers += "<tr bgcolor='#F5F5FF'><th colspan='3' class='table-title'><b>All Failures</b></th>" +
        "<th class='table-title'>Base Branch</th></tr>";

    return blockers;
}

/**
 * Copy suite and remove flaky tests from the copy.
 *
 * @param suite - see SuiteCurrentStatus Java class.
 * @returns Suite without flaky tests. Or null - if suite have only flaky tests.
 */
function filterPossibleBlocker(suite) {
    var suite0 = Object.assign({}, suite);

    var j = 0;

    suite0.testFailures = suite0.testFailures.slice();

    while (j < suite0.testFailures.length) {
        var testFailure = suite0.testFailures[j];

        if (isDefinedAndFilled(testFailure.blockerComment) && testFailure.blockerComment !== "")
            j++;
        else
            suite0.testFailures.splice(j, 1);
    }

    if(isDefinedAndFilled(suite.blockerComment) && suite.blockerComment!=="")
        return suite0;

    if (suite0.testFailures.length > 0)
        return suite0;

    return null;
}

function triggerBuilds(tcServerCode, parentSuiteId, suiteIdList, branchName, top, observe, ticketId, prNum, baseBranchForTc, cleanRebuild=false) {
    var queueAtTop = isDefinedAndFilled(top) && top;
    var observeJira = isDefinedAndFilled(observe) && observe;
    var suiteIdsNotExists = !isDefinedAndFilled(suiteIdList) || suiteIdList.length === 0;
    var branchNotExists = !isDefinedAndFilled(branchName) || branchName.length === 0;
    branchName = branchNotExists ? null : branchForTc(branchName);
    ticketId = (isDefinedAndFilled(ticketId) && ticketId.length > 0) ? ticketId : null;
    prNum = (isDefinedAndFilled(prNum) && prNum.length > 0) ? prNum : null;

    var triggerConfirm = $("#triggerConfirm");

    if (suiteIdsNotExists || branchNotExists) {
        triggerConfirm.html("No " + (suiteIdsNotExists ? "suites" +
            (branchNotExists ? " and branch" : "") : "branch") + " to run!");
        triggerConfirm.dialog({
            modal: true,
            buttons: {
                "Ok" : closeDialog
            }
        });

        return;
    }

    var suites = suiteIdList.split(',');
    var parentSuite = isDefinedAndFilled(parentSuiteId) ? parentSuiteId : suites[0];
    var fewSuites = suites.length > 1;

    var message = "Trigger build" + (fewSuites ? "s" : "") + " at <b>TC server:</b> " + tcServerCode + "<br>" +
    "<b>Branch:</b> " + branchName + "<br><b>Top:</b> " + top + "<br>" +
    "<b>Suite ID" + (fewSuites ? "s" : "") + ":</b> ";

    for (var i = 0; i < suites.length; i++)
        message += suites[i] + "<br>";

    if (fewSuites) {
        triggerConfirm.html(message);
        triggerConfirm.dialog({
            modal: true,
            buttons: {
                "Run" : function () {
                    $(this).dialog("close");
                    sendGetRequest();
                },
                "Cancel": closeDialog
            }
        });
    } else
        sendGetRequest();

    /**
     * See org.apache.ignite.ci.web.rest.TriggerBuilds#triggerBuilds
     */
    function sendGetRequest() {
        $.ajax({
            url: 'rest/build/trigger',
            data: {
                "srvCode": tcServerCode,
                "branchName": branchName,
                "parentSuiteId" : parentSuite,
                "suiteIdList": suiteIdList,
                "top": queueAtTop,
                "observe": observeJira,
                "ticketId": ticketId,
                "prNum": prNum,
                "baseBranchForTc": baseBranchForTc,
                "cleanRebuild": cleanRebuild
            },
            success: successDialog,
            error: showErrInLoadStatus
        });
    }

    function successDialog(result) {
        var triggerDialog = $("#triggerDialog");

        triggerDialog.html(message + "<br><b>Result:</b> " + result.result);
        triggerDialog.dialog({
            modal: true,
            buttons: {
                "Ok": closeDialog
            }
        });

        if (loadData && typeof(loadData) === "function")
            loadData();
    }

    function closeDialog() {
        $(this).dialog("close");
    }
}

/**
 * Converts PR number to branch for TeamCity.
 *
 * @param pr - Pull Request number.
 * @returns {String} Branch for TeamCity.
 */
function branchForTc(pr) {
    var regExpr = /(\d*)/i;

    if (regExpr.exec(pr)[0] === pr)
        return "pull/" + regExpr.exec(pr)[0] + "/head";

    return pr;
}

function commentJira(serverCode, branchName, parentSuiteId, ticketId, baseBranchForTc) {
    var branchNotExists = !isDefinedAndFilled(branchName) || branchName.length === 0;
    branchName = branchNotExists ? null : branchForTc(branchName);
    ticketId = (isDefinedAndFilled(ticketId) && ticketId.length > 0) ? ticketId : null;

    if (branchNotExists) {
        var triggerConfirm = $("#triggerConfirm");

        triggerConfirm.html("No branch to run!");
        triggerConfirm.dialog({
            modal: true,
            buttons: {
                "Ok" : function () {
                    $(this).dialog("close");
                }
            }
        });

        return;
    }

    $("#notifyJira").html("&#8987;" +
        " Please wait. First action for PR run-all data may require significant time.");

    $.ajax({
        url: 'rest/build/commentJira',
        data: {
            "serverId": serverCode, //general Servers code
            "suiteId": parentSuiteId,
            "branchName": branchName,
            "ticketId": ticketId,
            "baseBranchForTc": baseBranchForTc
        },
        success: function(result) {
            $("#notifyJira").html("");

            var needTicketId = result.result.lastIndexOf("TicketNotFoundException") !== -1;

            if (needTicketId) {
                var buttons = {
                    "Retry": function () {
                        $(this).dialog("close");

                        ticketId = $("#enterTicketId").val();

                        commentJira(serverCode, branchName, parentSuiteId, ticketId, baseBranchForTc)
                    },
                    "Cancel": function () {
                        $(this).dialog("close");
                    }
                }
            }
            else {
                buttons = {
                    "Ok": function () {
                        $(this).dialog("close");
                    }
                }
            }

            var dialog = $("#triggerDialog");

            dialog.html("Comment ticket for server: " + serverCode + "<br>" +
                " Suite: " + parentSuiteId + "<br>Branch:" + branchName +
                "<br><br> Result: " + result.result +
                (needTicketId ? ("<br><br>Enter JIRA ticket number: <input type='text' id='enterTicketId'>") : ""));

            dialog.dialog({
                modal: true,
                buttons: buttons
            });

            loadData(); // should be defined by page
        },
        error: showErrInLoadStatus
    });
}

/**
 * Create html string with table rows, containing suite data.
 *
 * @param suite - see org.apache.ignite.ci.web.model.current.SuiteCurrentStatus Java class.
 * @param settings - see Settings JavaScript class.
 * @param prNum - PR shown, used by triggering.
 * @returns {string} Table rows with suite data.
 */
function showSuiteData(suite, settings, prNum) {
    var moreInfoTxt = "";

    if (isDefinedAndFilled(suite.userCommits) && suite.userCommits !== "") {
        moreInfoTxt += "Last commits from: " + suite.userCommits + " <br>";
    }

    moreInfoTxt += "Duration: " + suite.durationPrintable + " " +
        "(Net Time: " + suite.durationNetTimePrintable + "," +
        " Tests: " + suite.testsDurationPrintable + "," +
        " Src. Update: " + suite.sourceUpdateDurationPrintable + "," +
        " Artifacts Publishing: " + suite.artifcactPublishingDurationPrintable + "," +
        " Dependecies Resolving: " + suite.dependeciesResolvingDurationPrintable + "," +
        " Timeouts: " + suite.lostInTimeouts + ")<br>";


    if(isDefinedAndFilled(suite.totalTests))
        moreInfoTxt += " <span title='Not muted and not ignored tests'>Total tests: " + suite.totalTests + "</span>";

    if(isDefinedAndFilled(suite.trustedTests))
        moreInfoTxt += " <span title='Tests which not filtered out because of flakyness'>Trusted tests: " + suite.trustedTests + "</span>";

    moreInfoTxt += "<br>";

    var res = "<tr class='suiteBlock'><td colspan='4' style='width: 100%'>" +
              "<table style='width: 100%' border='0px'>";

    res += "<tr bgcolor='#FAFAFF'><td align='right' valign='top' width='10%' colspan='2'>";

    var failRateText = "";
    if (isDefinedAndFilled(suite.failures) && isDefinedAndFilled(suite.runs) && isDefinedAndFilled(suite.failureRate)) {
        failRateText += "(fail rate " + suite.failureRate + "%)";

        moreInfoTxt += "Recent fails : "+ suite.failureRate +"% [" + suite.failures + " fails / " + suite.runs + " runs]; <br> " ;

        if(isDefinedAndFilled(suite.criticalFails) && isDefinedAndFilled(suite.criticalFails.failures)) {
            moreInfoTxt += "Critical recent fails: "+ suite.criticalFails.failureRate + "% [" + suite.criticalFails.failures + " fails / " + suite.criticalFails.runs + " runs]; <br> " ;
        }
    }

    if(isDefinedAndFilled(suite.blockerComment) && suite.blockerComment!=="") {
        res += "<span title='"+ suite.blockerComment +"'> &#x1f6ab;</span> "
    }

    if(isDefinedAndFilled(suite.problemRef)) {
        res += "<span title='"+ suite.problemRef.name +"'>&#128030;</span> "
    }

    var color;

    if(isDefinedAndFilled(suite.success) && suite.success===true) {
        color = 'green';
    } else {
        color = failureRateToColor(suite.failureRate);
    }

    if (isDefinedAndFilled(suite.latestRuns)) {
        res += drawLatestRuns(suite.latestRuns) + " ";
    }

    res +="</td><td>";
    res += "<span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='" + failRateText + "'></span> ";

    res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ " + "<a href='" + suite.webToBuild + "' title=''> " +
        "tests " + suite.failedTests + " " + suite.result;

    if (isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length > 0) {
        res += " warn " + suite.warnOnly.length;
    }

    res += "</a> ]";

    if(isDefinedAndFilled(suite.tags)) {
        for (let i = 0; i < suite.tags.length; i++) {
            const tag = suite.tags[i];
            res += " <span class='buildTag'>" + tag + "</span>" ;
        }
    }

    if (isDefinedAndFilled(suite.runningBuildCount) && suite.runningBuildCount !== 0) {
        res += " <img src='https://image.flaticon.com/icons/png/128/2/2745.png' width=12px height=12px> ";
        res += " " + suite.runningBuildCount + " running";
    }
    if (isDefinedAndFilled(suite.queuedBuildCount) && suite.queuedBuildCount !== 0) {
        res += " <img src='https://d30y9cdsu7xlg0.cloudfront.net/png/273613-200.png' width=12px height=12px> ";
        res += "" + suite.queuedBuildCount + " queued";
    }

    var mInfo = "";
    if (isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
        mInfo += " Trigger build: ";
        mInfo += "<a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuilds(\"" + suite.serverId + "\", null, \"" +
            suite.suiteId + "\", \"" + suite.branchName + "\", false, false, null, \"" + prNum + "\", null, false)' ";
        mInfo += " title='trigger build' >queue</a> ";

        mInfo += "<a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuilds(\"" + suite.serverId + "\", null, \"" +
            suite.suiteId + "\", \"" + suite.branchName + "\", true, false, null, \"" + prNum + "\", null, false)' ";
        mInfo += " title='trigger build at top of queue'>top</a><br>";
    }

    mInfo += moreInfoTxt;

    if (isDefinedAndFilled(suite.topLongRunning) && suite.topLongRunning.length > 0) {
        mInfo += "Top long running:<br>";

        mInfo += "<table>";
        for (var j = 0; j < suite.topLongRunning.length; j++) {
            mInfo += showTestFailData(suite.topLongRunning[j], false, settings);
        }
        mInfo += "</table>";
    }

    if (isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length > 0) {
        mInfo += "Warn Only:<br>";
        mInfo += "<table>";
        for (var k = 0; k < suite.warnOnly.length; k++) {
            mInfo += showTestFailData(suite.warnOnly[k], false, settings);
        }
        mInfo += "</table>";
    }

    if (isDefinedAndFilled(suite.logConsumers) && suite.logConsumers.length > 0) {
        mInfo += "Top Log Consumers:<br>";
        mInfo += "<table>";
        for (var l = 0; l < suite.logConsumers.length; l++) {
            mInfo += showTestFailData(suite.logConsumers[l], false, settings);
        }
        mInfo += "</table>";
    }

    if(!isDefinedAndFilled(findGetParameter("reportMode"))) {
        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>" + more + "</a>";
        res += "<div class='content'>" + mInfo + "</div></span>";
    }

    res += "</td>";

    res += "<td width='25%'>"; //fail rate
    if(isDefinedAndFilled(suite.hasCriticalProblem) && suite.hasCriticalProblem
        && isDefinedAndFilled(suite.criticalFails) && isDefinedAndFilled(suite.criticalFails.failures)) {
        res += "<a href='" + suite.webToHistBaseBranch + "'>";
        res += "Critical F.R.: "+ suite.criticalFails.failureRate + "% </a> " ;
    } else {
        res+="&nbsp;";
    }
    res += "</td>"; //fail rate
    res += " </tr>";

    for (var i = 0; i < suite.testFailures.length; i++) {
        var testFailure = suite.testFailures[i];

        res += showTestFailData(testFailure, true, settings);
    }

    if (isDefinedAndFilled(suite.webUrlThreadDump)) {
        res += "<tr><td colspan='2'></td><td>&nbsp; &nbsp; <a href='" + suite.webUrlThreadDump + "'>";
        res += "<img src='https://cdn2.iconfinder.com/data/icons/metro-uinvert-dock/256/Services.png' width=12px height=12px> ";
        res += "Thread Dump</a>";
        res += "<td>&nbsp;</td>";
        res += "</td></tr>";
    }


    res += "<tr><td>&nbsp;</td><td width='12px'>&nbsp;</td><td colspan='2'></td></tr>";

    res += "</tr></table>"

    return res;
}

function failureRateToColor(failureRate) {
    var redSaturation = 255;
    var greenSaturation = 0;
    var blueSaturation = 0;

    var colorCorrect = 0;
    if (isDefinedAndFilled(failureRate)) {
        colorCorrect = parseFloat(failureRate);
    }

    if (colorCorrect < 50) {
        redSaturation = 255;
        greenSaturation += colorCorrect * 5;
    } else {
        greenSaturation = 255 - (colorCorrect - 50) * 5;
        redSaturation = 255 - (colorCorrect - 50) * 5;
    }
    return rgbToHex(redSaturation, greenSaturation, blueSaturation);
}


//@param testFail - see DsTestFailureUi
function showTestFailData(testFail, isFailureShown, settings) {
    var failRateDefined =
        isDefinedAndFilled(testFail.histBaseBranch)
        && isDefinedAndFilled(testFail.histBaseBranch.recent)
        && isDefinedAndFilled(testFail.histBaseBranch.recent.failureRate);

    var failRate = failRateDefined ? testFail.histBaseBranch.recent.failureRate : null;

    var flakyCommentsInBase =
        isDefinedAndFilled(testFail.histBaseBranch) && isDefinedAndFilled(testFail.histBaseBranch.flakyComments)
            ? testFail.histBaseBranch.flakyComments
            : null;

    let shownBecauseOfDuration = (isDefinedAndFilled(testFail.success) && testFail.success === true) || !isFailureShown;

    if (!shownBecauseOfDuration) {
        if (failRate != null) {
            if (parseFloat(failRate) < settings.minFailRate)
                return ""; //test is hidden

            if (parseFloat(failRate) > settings.maxFailRate)
                return ""; //test is hidden
        }

        if (flakyCommentsInBase != null && settings.hideFlakyFailures)
            return ""; // test is hidden
    }

    var res = "<tr><td align='right' valign='top' colspan='2' width='10%'>";

    var haveIssue = isDefinedAndFilled(testFail.webIssueUrl) && isDefinedAndFilled(testFail.webIssueText);

    var color;
    if (testFail.success === true) {
        color = "green";
    } else {
        color = (isFailureShown && failRateDefined)
            ? failureRateToColor(failRate)
            : "white";
    }

    var investigated = isDefinedAndFilled(testFail.investigated) && testFail.investigated;
    if (investigated) {
        res += "<img src='https://d30y9cdsu7xlg0.cloudfront.net/png/324212-200.png' width=11px height=11px> ";
        res += "<span style='opacity: 0.75'> ";
    }

    // has both base and current, draw current latest runs here.
    var comparePage =
        findGetParameter('action') != null
        || (
            isDefinedAndFilled(testFail.histCurBranch) && isDefinedAndFilled(testFail.histCurBranch.latestRuns)
            && isDefinedAndFilled(testFail.histBaseBranch) && isDefinedAndFilled(testFail.histBaseBranch.latestRuns)
        );

    var baseBranchMarks = "";

    if (isFailureShown && flakyCommentsInBase != null) {
        baseBranchMarks += "<span title='" + flakyCommentsInBase + "' style=\"color: #303030; font-size: 125%;\">" + "&#9858;" + "</span> "; //&asymp;
    }

    if (comparePage) {
        var flakyCommentsInCur =
            isDefinedAndFilled(testFail.histCurBranch) && isDefinedAndFilled(testFail.histCurBranch.flakyComments)
                ? testFail.histCurBranch.flakyComments
                : null;

        if(flakyCommentsInCur!=null)
            res += "<span title='" + flakyCommentsInCur + "' style=\"color: #303030; font-size: 125%;\">" + "&#9858;" + "</span> "; //&asymp;
    }
    else {
        res += baseBranchMarks;
    }

    if (isDefinedAndFilled(testFail.blockerComment) && testFail.blockerComment !== "") {
        res += "<span title='" + testFail.blockerComment + "'> &#x1f6ab;</span> "
    }

    var bold = false;
    if(isFailureShown && isDefinedAndFilled(testFail.problemRef)) {
        res += "<span title='"+testFail.problemRef.name +"'>&#128030;</span>";
        if(!bold)
           res += "<b>";

        bold = true;
    }

    var haveWeb = isDefinedAndFilled(testFail.webUrl);
    if (haveWeb)
        res += "<a href='" + testFail.webUrl + "'>";

    if(comparePage) {
        if (isDefinedAndFilled(testFail.histCurBranch))
            res += drawLatestRuns(testFail.histCurBranch.latestRuns);
    } else if(isDefinedAndFilled(testFail.histBaseBranch) && isDefinedAndFilled(testFail.histBaseBranch.latestRuns))
        res += drawLatestRuns(testFail.histBaseBranch.latestRuns); // has only base branch

    if (haveWeb)
        res += "</a> ";

    res += "</td><td valign='top'>";
    res += "<span style='background-color: " + color + "; width:8px; height:8px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";

    if (isDefinedAndFilled(testFail.curFailures) && testFail.curFailures > 1)
        res += "[" + testFail.curFailures + "] ";

    if (haveIssue) {
        res += "<a href='" + testFail.webIssueUrl + "'>";
        res += testFail.webIssueText;
        res += "</a>";
        res += ": ";
    }

    if (isDefinedAndFilled(testFail.suiteName) && isDefinedAndFilled(testFail.testName))
        res += "<font color='grey'>" + testFail.suiteName + ":</font> " + testFail.testName;
    else
        res += testFail.name;

    var histContent = "";

    //see class TestHistory
    var hist;

    if(isDefinedAndFilled(testFail.histBaseBranch))
        hist = testFail.histBaseBranch;
    else
        hist = null;

    if (isFailureShown && hist!=null) {
        if(comparePage)  {
             histContent += baseBranchMarks + " ";
        }

        var testFailTitle = "";

        if(isDefinedAndFilled(hist.recent) && isDefinedAndFilled(hist.recent.failures))
            testFailTitle = "recent rate: " + hist.recent.failures + " fails / " + hist.recent.runs + " runs" ;

        histContent += " <span title='" +testFailTitle + "'>";
        
        if (isDefinedAndFilled(hist.recent) && isDefinedAndFilled(hist.recent.failureRate))
            histContent += "(fail rate " + hist.recent.failureRate + "%)";
        else
            histContent += "(no data)";

        histContent += "</span>";

        if(comparePage && isDefinedAndFilled(testFail.histBaseBranch) && isDefinedAndFilled(testFail.histBaseBranch.latestRuns))  {
             histContent += " " + drawLatestRuns(testFail.histBaseBranch.latestRuns); // has both base and current, draw current base runs here.
        }
    } else if (haveWeb) {
        histContent += " (test history)";
    }


    if (shownBecauseOfDuration && isDefinedAndFilled(testFail.durationPrintable))
        res += " duration " + testFail.durationPrintable;

    if (bold)
        res += "</b>";

    if (investigated)
        res += "</span> ";


    if (isDefinedAndFilled(testFail.warnings) && testFail.warnings.length > 0
        && !isDefinedAndFilled(findGetParameter("reportMode"))) {
        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>" + more + "</a>";

        res += "<div class='content'>";

        res += "<p class='logMsg'>";
        for (var i = 0; i < testFail.warnings.length; i++) {
            res += "&nbsp; &nbsp; ";
            res += "&nbsp; &nbsp; ";
            res += testFail.warnings[i];
            res += " <br>";
        }
        res += "</p>";

        res += "</div></span>";

    }

    res += "<td width='25%'>";

    var haveBaseBranchWeb = isDefinedAndFilled(testFail.webUrlBaseBranch);
    if (haveBaseBranchWeb)
        res += "<a href='" + testFail.webUrlBaseBranch + "'>";

    res += histContent;

    if (haveBaseBranchWeb)
        res += "</a>";

    res += "&nbsp;</td>";

    res += "</td></tr>";

    return res;
}

function drawLatestRuns(latestRuns) {
    if(isDefinedAndFilled(findGetParameter("reportMode")))
        return "";

    var res = "";
    res += "<nobr><span style='white-space: nowrap; width:" + (latestRuns.length  * 1) + "px; display: inline-block;' " +
        "title='Latest master runs history from right to left is oldest to newest." +
        " Red-failed,green-passed,black-critical failure'>";

    var len = 1;
    var prevState = null;
    for (var i = 0; i < latestRuns.length; i++) {
        var runCode = latestRuns[i];

        if (prevState == null) {
            //skip
        } else if (prevState === runCode) {
            len++;
        } else {
            res += drawLatestRunsBlock(prevState, len);
            len = 1;
        }

        prevState = runCode;
    }
    if (prevState != null) {
        res += drawLatestRunsBlock(prevState, len);
    }
    res += "</span></nobr>";

    return res;
}

function drawLatestRunsBlock(state, len) {
    var runColor = "white";

    const resOk = 0;
    if (state === resOk)
        runColor = "green";
    else if (state === 1)
        runColor = "red";
    else if (state === 2) // deprected MUTED
        runColor = "grey";
    else if (state === 3) // CRITICAL failure - incomplete suite
        runColor = "#000000";
    else if (state === 4) // RES_MISSING(4),  missing in run
        runColor = "#AAAAAA";
    else if (state === 5)   // RES_OK_MUTED(5),
        runColor = "#44AA44";
    else if (state === 6)  // RES_FAILURE_MUTED(6),
        runColor = "#b76a6a";
    else if (state === 7) //  RES_IGNORED(7);
        runColor = "#F09F00";

    return "<span style='background-color: " + runColor + ";  width:" + (len * 1) + "px; height:10px; display: inline-block;'></span>";
}