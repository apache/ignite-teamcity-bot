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
    var minFailRate = parseFloat(findGetParameter("minFailRate") || 0);

    var maxFailRate = parseFloat(findGetParameter("maxFailRate") || 100);

    var hideFlakyFailures = findGetParameter("hideFlakyFailures") === "true";

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
    var newTestRows = "";
    var newTestsCnt = 0;

    res += "<table style='width:100%'>";

    for (var i = 0; i < chain.length; i++) {
        var newTests = chain[i].tests;
        newTestRows += "<tr><td colspan='2' width='10%'></td>";
        newTestRows += "<td colspan='2' width='80%'><a href='" + chain[i].webToBuild + "'>" + chain[i].name + "</a>" + "</td></tr>";
        newTestRows += "<td colspan='2' width='10%'></td>";
        for (var j = 0; j < newTests.length; j++) {
            newTestsCnt++;
            var newTest = newTests[j];
            var testColor = newTest.status ? "#013220" : "#8b0000";
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

    if (newTestRows !== "") {
        res += "<tr><td colspan='4'>New tests: " + newTestsCnt +
            "<table style='width:100%'>" + newTestRows + "</table></td></tr>";
    }
    else
        res += "<tr><td colspan='2' width='10%'></td><td width='90%'>No new tests</td></tr>";

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

        if (!isSuiteProblematic(suite))
            continue;

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
        moreInfoTxt += " onClick='" + jsCallAttr("triggerBuilds", [srvCodeForTriggering, parentSuitId,
            suitesFailedList, chain.branchName, false, false, null, chain.prNum, null, false]) + "' ";
        moreInfoTxt += " title='trigger builds'>in queue</a> ";

        moreInfoTxt += " <a href='javascript:void(0);' ";
        moreInfoTxt += " onClick='" + jsCallAttr("triggerBuilds", [srvCodeForTriggering, parentSuitId,
            suitesFailedList, chain.branchName, true, false, null, chain.prNum, null, false]) + "' ";
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

    let baseBranchForTc = chain.baseBranchForTc;
    let actionButtons = "";
    let hasCommentContext = isDefinedAndFilled(chain.prNum) || isDefinedAndFilled(chain.webToTicket);
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

    if (settings.isTeamCityAvailable() && blockersList.length !== 0 &&
        isDefinedAndFilled(srvCodeForTriggering) && isDefinedAndFilled(chain.branchName)) {
        actionButtons += "<button onclick='" + jsCallAttr("triggerBuildsWithCommentOptions", [
            srvCodeForTriggering, parentSuitId, blockersList, chain.branchName,
            false, false, null, chain.prNum, baseBranchForTc, false,
            isDefinedAndFilled(chain.webToTicket) ? chain.webToTicket : "",
            isDefinedAndFilled(chain.webToPr) ? chain.webToPr : "",
            hasCommentContext
        ]) + "'>Rerun blockers</button>";
    }

    if (settings.isTeamCityAvailable() && suitesFailedList.length !== 0 &&
        isDefinedAndFilled(srvCodeForTriggering) && isDefinedAndFilled(chain.branchName)) {
        if (actionButtons.length !== 0)
            actionButtons += " ";

        if (hasCommentContext) {
            actionButtons += "<button onclick='" + jsCallAttr("triggerBuildsWithCommentOptions", [
                srvCodeForTriggering, parentSuitId, suitesFailedList, chain.branchName,
                false, false, null, chain.prNum, baseBranchForTc, false,
                isDefinedAndFilled(chain.webToTicket) ? chain.webToTicket : "",
                isDefinedAndFilled(chain.webToPr) ? chain.webToPr : "",
                true
            ]) + "'>Trigger failed builds</button>";
        }
        else {
            actionButtons += "<button onclick='" + jsCallAttr("triggerBuilds", [
                srvCodeForTriggering, parentSuitId, suitesFailedList, chain.branchName,
                false, false, null, chain.prNum, baseBranchForTc, false
            ]) + "'>Trigger failed builds</button>";
        }
    }
    else if (settings.isTeamCityAvailable() && isDefinedAndFilled(srvCodeForTriggering) &&
        isDefinedAndFilled(chain.branchName)) {
        actionButtons += "<button class='disabledbtn' disabled title='No failed suites to trigger'>" +
            "Trigger failed builds</button>";
    }

    if (hasCommentContext && (settings.isJiraAvailable() || settings.isGithubAvailable()) &&
        isDefinedAndFilled(srvCodeForTriggering)) {
        let commentBtns = "<span style='display:inline-flex; gap:4px; align-items:center; white-space:nowrap'>";

        if (settings.isJiraAvailable()) {
            commentBtns += "<button onclick='" + jsCallAttr("commentJira", [
                srvCodeForTriggering, chain.branchName, parentSuitId, "",
                baseBranchForTc, "JIRA", chain.prNum, false,
                {
                    ticketLink: isDefinedAndFilled(chain.webToTicket) ? chain.webToTicket : "",
                    prLink: isDefinedAndFilled(chain.webToPr) ? chain.webToPr : ""
                }
            ]) + "'>" + buttonLabel("Comment JIRA", chain.webToTicket, null) + "</button>";
        }

        if (settings.isGithubAvailable() && isDefinedAndFilled(chain.prNum)) {
            commentBtns += "<button onclick='" + jsCallAttr("commentJira", [
                srvCodeForTriggering, chain.branchName, parentSuitId, "",
                baseBranchForTc, "GITHUB", chain.prNum, false,
                {
                    ticketLink: isDefinedAndFilled(chain.webToTicket) ? chain.webToTicket : "",
                    prLink: isDefinedAndFilled(chain.webToPr) ? chain.webToPr : ""
                }
            ]) + "'>" + buttonLabel("Comment GitHub PR", chain.webToPr, null) + "</button>";
        }

        commentBtns += "</span><br>";
        res += commentBtns;
    }

    if (actionButtons.length !== 0) {
        res += "<span style='display:inline-flex; gap:4px; align-items:center; white-space:nowrap'>" +
            actionButtons + "</span><br>";
    }

    if (isDefinedAndFilled(baseBranchForTc)) {
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
    if (!isSuiteProblematic(suite))
        return null;

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

function selectCommentTargets(defaultTargets, onSelected) {
    selectCommentOptions(defaultTargets, {}, function (selectedTargets) {
        onSelected(selectedTargets);
    });
}

function selectCommentOptions(defaultTargets, options, onSelected) {
    var targets = defaultTargets !== null && typeof defaultTargets !== "undefined" ? defaultTargets : "JIRA";
    var hasJira = targets.indexOf("JIRA") !== -1;
    var hasGithub = targets.indexOf("GITHUB") !== -1;
    var dialog = ensureActionDialog("triggerConfirm", "Comment targets");
    var ticketLink = options && isDefinedAndFilled(options.ticketLink) ?
        optionalLink(options.ticketLink, ticketLinkLabel(options.ticketLink, "ticket")) : "";
    var prLink = options && isDefinedAndFilled(options.prLink) ?
        optionalLink(options.prLink, prLinkLabel(options.prLink, "PR")) : "";
    var allowNoTargets = options && options.allowNoTargets;
    var showOnlyNoBlockers = options && options.showOnlyNoBlockers;

    dialog.html(
        "<div>Select where TCBot should publish the analysis after results are ready.</div><br>" +
        "<label><input type='checkbox' id='commentTargetJira' " + (hasJira ? "checked" : "") + "> JIRA" +
        ticketLink + "</label><br>" +
        "<label><input type='checkbox' id='commentTargetGithub' " + (hasGithub ? "checked" : "") + "> GitHub PR" +
        prLink + "</label>" +
        (showOnlyNoBlockers
            ? "<br><label><input type='checkbox' id='commentOnlyIfNoBlockers'> Comment only if no blockers</label>"
            : "")
    );

    openActionDialog(dialog, "Comment targets", {
            "Continue": function () {
                var selected = [];

                if ($("#commentTargetJira").prop("checked"))
                    selected.push("JIRA");

                if ($("#commentTargetGithub").prop("checked"))
                    selected.push("GITHUB");

                if (selected.length === 0 && !allowNoTargets)
                    return;

                var commentOnlyIfNoBlockers = showOnlyNoBlockers &&
                    $("#commentOnlyIfNoBlockers").prop("checked");

                $(this).dialog("close");
                onSelected(selected.join(","), commentOnlyIfNoBlockers);
            },
            "Cancel": function () {
                $(this).dialog("close");
            }
        });
}

function triggerBuildsWithCommentOptions(tcServerCode, parentSuiteId, suiteIdList, branchName, top, observe, ticketId,
    prNum, baseBranchForTc, cleanRebuild=false, ticketLink, prLink, showCommentOptions=true) {
    triggerBuilds(tcServerCode, parentSuiteId, suiteIdList, branchName, top, observe, ticketId, prNum,
        baseBranchForTc, cleanRebuild, "", false, {
        ticketLink: ticketLink,
        prLink: prLink,
        showCommentOptions: showCommentOptions
    });
}

function triggerBuilds(tcServerCode, parentSuiteId, suiteIdList, branchName, top, observe, ticketId, prNum, baseBranchForTc,
    cleanRebuild=false, commentTargets, commentOnlyIfNoBlockers=false, uiOptions) {
    var queueAtTop = isDefinedAndFilled(top) && top;
    var observeJira = isDefinedAndFilled(observe) && observe;
    var suiteIdsNotExists = !isDefinedAndFilled(suiteIdList) || suiteIdList.length === 0;
    var branchNotExists = !isDefinedAndFilled(branchName) || branchName.length === 0;
    branchName = branchNotExists ? null : branchForTc(branchName);
    ticketId = (isDefinedAndFilled(ticketId) && ticketId.length > 0) ? ticketId : null;
    prNum = (isDefinedAndFilled(prNum) && prNum.length > 0) ? prNum : null;

    var triggerConfirm = ensureActionDialog("triggerConfirm", "Build trigger");

    if (suiteIdsNotExists || branchNotExists) {
        triggerConfirm.html("No " + (suiteIdsNotExists ? "suites" +
            (branchNotExists ? " and branch" : "") : "branch") + " to run!");
        openActionDialog(triggerConfirm, "Build trigger", {
                "Ok" : closeDialog
            });

        return;
    }

    var suites = suiteIdList.split(',');
    var parentSuite = isDefinedAndFilled(parentSuiteId) ? parentSuiteId : suites[0];
    var fewSuites = suites.length > 1;
    var processId = createBotProcessId("triggerBuilds");
    var stopProcessPolling;

    var message = "<b>TC server:</b> " + escapeHtml(tcServerCode) + "<br>" +
        "<b>Branch:</b> " + escapeHtml(branchName) + "<br>" +
        "<b>Suite ID" + (fewSuites ? "s" : "") + ":</b> " + suitesSummaryHtml(suites);

    showTriggerStagesDialog();

    /**
     * See org.apache.ignite.ci.web.rest.TriggerBuilds#triggerBuildsAsync
     */
    function sendGetRequest() {
        $.ajax({
            url: 'rest/build/triggerBuildsAsync',
            data: {
                "srvCode": tcServerCode,
                "branchName": branchName,
                "parentSuiteId" : parentSuite,
                "suiteIdList": suiteIdList,
                "top": queueAtTop,
                "observe": observeJira,
                "comment": commentTargets,
                "ticketId": ticketId,
                "prNum": prNum,
                "baseBranchForTc": baseBranchForTc,
                "commentOnlyIfNoBlockers": commentOnlyIfNoBlockers,
                "cleanRebuild": cleanRebuild,
                "processId": processId
            },
            success: function(result) {
                appendActionStage(triggerConfirm, simpleResultText(result));
            },
            error: failureDialog
        });
    }

    function finishTriggerProcess(resultText) {
        var failed = resultText.indexOf("Failed:") === 0 || resultText.indexOf("Error:") === 0;

        setActionStatus(triggerConfirm, failed ? "Build trigger request failed." : "Build trigger request is complete.");
        appendActionStage(triggerConfirm, "Result: " + actionSummaryText(resultText));

        if (failed)
            showActionError(triggerConfirm, resultText);

        if (!failed && observeJira)
            appendActionStage(triggerConfirm, "Comment scheduled: " + commentTargetsLabel(commentTargets) +
                "; policy: " + commentPolicyLabel(commentOnlyIfNoBlockers) + ".");
        else if (!failed)
            appendActionStage(triggerConfirm, "No result comment scheduled.");

        triggerConfirm.dialog("option", "buttons", {"Ok": closeDialog});

        if (loadData && typeof(loadData) === "function")
            loadData();
    }

    function failureDialog(jqXHR, exception) {
        if (stopProcessPolling)
            stopProcessPolling();

        setActionStatus(triggerConfirm, "Build trigger request failed.");
        appendActionStage(triggerConfirm, "Trigger request failed.");
        triggerConfirm.find(".action-error").text(jqXHR.responseText || exception).show();
        triggerConfirm.dialog("option", "buttons", {"Ok": closeDialog});
        showErrInLoadStatus(jqXHR, exception);
    }

    function showTriggerStagesDialog() {
        var defaultTargets = isDefinedAndFilled(commentTargets)
            ? commentTargets : (observeJira ? "JIRA" : "");
        var hasJira = defaultTargets.indexOf("JIRA") !== -1;
        var hasGithub = defaultTargets.indexOf("GITHUB") !== -1;
        var opts = uiOptions || {};
        var showCommentOptions = opts.showCommentOptions || observeJira || isDefinedAndFilled(commentTargets);
        var commentPolicyHint = opts.commentPolicyHint
            ? "<div style='margin-top:8px; color:#666; font-size:12px'>" +
            escapeHtml(opts.commentPolicyHint) + "</div>"
            : "";
        var commentOptionsHtml = showCommentOptions
            ? "<div style='margin-top:12px'><b>Comment after build</b></div>" +
            "<div style='display:flex; gap:24px; align-items:flex-start'>" +
            "<div>" +
            "<label><input type='checkbox' id='stageCommentJira' " + (hasJira ? "checked" : "") + "> JIRA" +
            optionalLink(opts.ticketLink, ticketLinkLabel(opts.ticketLink, "ticket")) + "</label><br>" +
            "<label><input type='checkbox' id='stageCommentGithub' " + (hasGithub ? "checked" : "") +
            "> GitHub PR" + optionalLink(opts.prLink, prLinkLabel(opts.prLink, "PR")) + "</label><br>" +
            "</div>" +
            "<div class='stage-comment-policy'>" +
            "<label><input type='radio' name='stageCommentPolicy' value='always' " +
            (!commentOnlyIfNoBlockers ? "checked" : "") + "> Comment always</label><br>" +
            "<label><input type='radio' name='stageCommentPolicy' value='clean' " +
            (commentOnlyIfNoBlockers ? "checked" : "") + "> Comment only clean run (no blockers)</label>" +
            commentPolicyHint +
            "</div></div>"
            : "";

        triggerConfirm.html(
            actionStatusHtml("Configure build trigger.") +
            "<div style='margin-bottom:12px'>" + message + "</div>" +
            "<div style='margin-top:12px'><b>Run options</b></div>" +
            "<label><input type='checkbox' id='stageQueueAtTop' " + (queueAtTop ? "checked" : "") +
            "> Put builds at top of queue</label>" +
            "<div style='margin-left:22px; color:#666; font-size:12px'>Requires TeamCity permission to reorder build queue.</div>" +
            "<label><input type='checkbox' id='stageCleanRebuild' " + (cleanRebuild ? "checked" : "") +
            "> Clean build</label>" +
            "<div style='margin-left:22px; color:#666; font-size:12px'>Delete checkout files before snapshot dependency builds.</div>" +
            commentOptionsHtml +
            actionStagesHtml(true) +
            actionErrorHtml()
        );

        bindCommentPolicyVisibility(triggerConfirm);

        openActionDialog(triggerConfirm, "Build trigger", {
                "Run": function () {
                    queueAtTop = triggerConfirm.find("#stageQueueAtTop").prop("checked");
                    cleanRebuild = triggerConfirm.find("#stageCleanRebuild").prop("checked");

                    if (showCommentOptions) {
                        commentTargets = collectStageCommentTargets(triggerConfirm);
                        commentOnlyIfNoBlockers = isDefinedAndFilled(commentTargets) &&
                            triggerConfirm.find("input[name='stageCommentPolicy']:checked").val() === "clean";
                        observeJira = isDefinedAndFilled(commentTargets);
                    }
                    else {
                        commentTargets = "";
                        commentOnlyIfNoBlockers = false;
                        observeJira = false;
                    }

                    triggerConfirm.dialog("option", "buttons", {});
                    triggerConfirm.find("input").prop("disabled", true);
                    setActionStatus(triggerConfirm, "Build trigger is running...");
                    triggerConfirm.find(".action-stages").show().empty();
                    appendActionStage(triggerConfirm, "Options selected.");
                    appendActionStage(triggerConfirm, "Run mode: " + (cleanRebuild ? "clean checkout" : "regular") +
                        "; queue: " + (queueAtTop ? "top" : "regular") + ".");
                    appendActionStage(triggerConfirm, observeJira
                        ? "Will comment " + commentTargetsLabel(commentTargets) +
                        " with policy: " + commentPolicyLabel(commentOnlyIfNoBlockers) + "."
                        : "Commenting disabled.");
                    appendActionStage(triggerConfirm, "Sending trigger request to the bot REST API.");
                    stopProcessPolling = startBotProcessPolling(processId, function (processStatus) {
                        var statusText = botProcessStatusText(processStatus);

                        if (processStatus.running === false)
                            finishTriggerProcess(statusText);
                        else
                            appendActionStage(triggerConfirm, statusText);
                    });
                    sendGetRequest();
                },
                "Cancel": closeDialog
            });
    }

    function closeDialog() {
        if (stopProcessPolling)
            stopProcessPolling();

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

function commentJira(serverCode, branchName, parentSuiteId, ticketId, baseBranchForTc, commentTargets, prNum,
    commentOnlyIfNoBlockers=false, uiOptions) {
    var branchNotExists = !isDefinedAndFilled(branchName) || branchName.length === 0;
    branchName = branchNotExists ? null : branchForTc(branchName);
    ticketId = (isDefinedAndFilled(ticketId) && ticketId.length > 0) ? ticketId : null;
    var processId = createBotProcessId("commentBuildAnalysis");
    var stopProcessPolling;

    if (branchNotExists) {
        var triggerConfirm = ensureActionDialog("triggerConfirm", "Post comment");

        triggerConfirm.html("No branch to comment!");
        openActionDialog(triggerConfirm, "Post comment", {
                "Ok" : function () {
                    $(this).dialog("close");
                }
            });

        return;
    }

    showCommentStagesDialog();

    function sendCommentRequest(dialog) {
        $.ajax({
            url: 'rest/build/commentBuildAnalysis',
            data: {
                "serverId": serverCode, //general Servers code
                "suiteId": parentSuiteId,
                "branchName": branchName,
                "ticketId": ticketId,
                "baseBranchForTc": baseBranchForTc,
                "comment": commentTargets,
                "prNum": prNum,
                "commentOnlyIfNoBlockers": commentOnlyIfNoBlockers,
                "processId": processId
            },
            success: function(result) {
                appendActionStage(dialog, simpleResultText(result));
            },
            error: function(jqXHR, exception) {
                if (stopProcessPolling)
                    stopProcessPolling();

                setActionStatus(dialog, "Comment request failed.");
                appendActionStage(dialog, "Comment request failed.");
                showActionError(dialog, jqXHR.responseText || exception || "Unknown request error.");
                dialog.dialog("option", "buttons", {"Ok": closeDialog});
            }
        });
    }

    function finishCommentProcess(dialog, resultText) {
        setActionStatus(dialog, commentResultFailed(resultText)
            ? "Comment request failed."
            : "Comment request is complete.");
        appendActionStage(dialog, "Result: " + actionSummaryText(resultText));

        if (commentResultFailed(resultText))
            showActionError(dialog, resultText);
        else
            showActionResult(dialog, resultText);

        var needTicketId = resultText.lastIndexOf("TicketNotFoundException") !== -1;

        if (needTicketId) {
            dialog.append("<div style='margin-top:12px'>Enter JIRA ticket number: " +
                "<input type='text' id='enterTicketId'></div>");

            dialog.dialog("option", "buttons", {
                "Retry": function () {
                    ticketId = $("#enterTicketId").val();
                    processId = createBotProcessId("commentBuildAnalysis");
                    showCommentProcessDialog(dialog);
                    appendActionStage(dialog, "Retrying with explicit ticket " + ticketId + ".");
                    appendActionStage(dialog, "Sending comment request to the bot REST API.");
                    stopProcessPolling = startCommentProcessPolling(dialog);
                    sendCommentRequest(dialog);
                },
                "Cancel": closeDialog
            });
        }
        else
            dialog.dialog("option", "buttons", {"Ok": closeDialog});

        if (loadData && typeof(loadData) === "function")
            loadData();
    }

    function startCommentProcessPolling(dialog) {
        return startBotProcessPolling(processId, function (processStatus) {
            var statusText = botProcessStatusText(processStatus);

            if (processStatus.running === false)
                finishCommentProcess(dialog, statusText);
            else
                appendActionStage(dialog, statusText);
        });
    }

    function showCommentStagesDialog() {
        var dialog = ensureActionDialog("triggerDialog", "Post comment");
        var defaultTargets = isDefinedAndFilled(commentTargets) ? commentTargets : "JIRA";
        var opts = uiOptions || {};

        dialog.html(
            actionStatusHtml("Configure comment publishing.") +
            "<div style='margin-bottom:12px'>Server: " + escapeHtml(serverCode) +
            "<br>Suite: " + escapeHtml(parentSuiteId) +
            "<br>Branch: " + escapeHtml(branchName) + "</div>" +
            "<div style='margin-top:12px'><b>Targets</b></div>" +
            "<div style='display:flex; gap:24px; align-items:flex-start'>" +
            "<div>" +
            "<label><input type='checkbox' id='stageCommentJira' " +
            (defaultTargets.indexOf("JIRA") !== -1 ? "checked" : "") + "> JIRA" +
            optionalLink(opts.ticketLink, ticketLinkLabel(opts.ticketLink, "ticket")) + "</label><br>" +
            "<label><input type='checkbox' id='stageCommentGithub' " +
            (defaultTargets.indexOf("GITHUB") !== -1 ? "checked" : "") + "> GitHub PR" +
            optionalLink(opts.prLink, prLinkLabel(opts.prLink, "PR")) + "</label><br>" +
            "</div>" +
            "<div class='stage-comment-policy'>" +
            "<label><input type='radio' name='stageCommentPolicy' value='always' " +
            (!commentOnlyIfNoBlockers ? "checked" : "") + "> Comment always</label><br>" +
            "<label><input type='radio' name='stageCommentPolicy' value='clean' " +
            (commentOnlyIfNoBlockers ? "checked" : "") + "> Comment only clean run (no blockers)</label></div>" +
            "</div>" +
            actionStagesHtml(true) +
            actionErrorHtml() +
            actionResultHtml()
        );

        bindCommentPolicyVisibility(dialog);

        openActionDialog(dialog, "Post comment", {
                "Post comment": function () {
                    commentTargets = collectStageCommentTargets(dialog);

                    if (!isDefinedAndFilled(commentTargets)) {
                        showActionError(dialog, "Select at least one comment target.");

                        return;
                    }

                    commentOnlyIfNoBlockers = dialog.find("input[name='stageCommentPolicy']:checked").val() === "clean";
                    showCommentProcessDialog(dialog);
                    appendActionStage(dialog, "Options selected.");
                    appendActionStage(dialog, "Will comment " + commentTargetsLabel(commentTargets) +
                        " with policy: " + commentPolicyLabel(commentOnlyIfNoBlockers) + ".");
                    appendActionStage(dialog, "Sending comment request to the bot REST API.");
                    stopProcessPolling = startCommentProcessPolling(dialog);
                    sendCommentRequest(dialog);
                },
                "Cancel": closeDialog
            });
    }

    function showCommentProcessDialog(dialog) {
        dialog.html(
            actionStatusHtml("Comment request is running...") +
            actionStagesHtml(false) +
            actionErrorHtml() +
            actionResultHtml()
        );
        dialog.dialog("option", "title", "Posting comment");
        dialog.dialog("option", "buttons", {});
    }

    function closeDialog() {
        if (stopProcessPolling)
            stopProcessPolling();

        $(this).dialog("close");
    }
}

function collectStageCommentTargets(dialog) {
    var selected = [];
    var root = dialog || $(document);

    if (root.find("#stageCommentJira").prop("checked"))
        selected.push("JIRA");

    if (root.find("#stageCommentGithub").prop("checked"))
        selected.push("GITHUB");

    return selected.join(",");
}

function bindCommentPolicyVisibility(dialog) {
    updateCommentPolicyVisibility(dialog);
    dialog.find("#stageCommentJira, #stageCommentGithub").off("change.commentPolicy").on("change.commentPolicy",
        function () {
            updateCommentPolicyVisibility(dialog);
        });
}

function updateCommentPolicyVisibility(dialog) {
    var hasTarget = isDefinedAndFilled(collectStageCommentTargets(dialog));
    var policy = dialog.find(".stage-comment-policy");

    if (hasTarget)
        policy.show();
    else
        policy.hide();
}

function appendActionStage(dialog, text) {
    var stages = dialog.find(".action-stages");
    var summary = actionSummaryText(text);

    ensureActionDialogVisible(dialog);

    stages.children(".action-stage").css({
        "color": "#666",
        "opacity": "0.58"
    });

    stages.show();
    stages.append($("<div>", {
        "class": "action-stage",
        css: {
            "color": "#222",
            "opacity": "1",
            "transition": "color 0.2s ease, opacity 0.2s ease"
        }
    }).text(summary));

    if (stages.length > 0)
        stages.scrollTop(stages[0].scrollHeight);
}

function ensureActionDialog(id, title) {
    var dialog = $("#" + id);

    if (dialog.data("ui-dialog"))
        dialog.dialog("destroy");

    dialog.remove();
    $(".ui-widget-overlay").remove();
    dialog = $("<div>", {id: id, title: title}).appendTo("body");

    return dialog;
}

function openActionDialog(dialog, title, buttons) {
    openCenteredDialog(dialog, actionDialogOptions(title, buttons));
}

function ensureActionDialogVisible(dialog) {
    if (!dialog || dialog.length === 0)
        return;

    if (!dialog.data("ui-dialog"))
        openActionDialog(dialog, dialog.attr("title") || "Process status", {});

    if (!dialog.dialog("isOpen"))
        dialog.dialog("open");

    dialog.dialog("moveToTop");
    centerDialogInViewport(dialog, $(window).scrollLeft(), $(window).scrollTop());
}

function actionStatusHtml(text) {
    return "<div class='action-status' style='font-weight:600; margin-bottom:12px'>" +
        escapeHtml(text) + "</div>";
}

function actionStagesHtml(hidden, id) {
    return "<div class='action-stages'" + (isDefinedAndFilled(id) ? " id='" + escapeHtml(id) + "'" : "") +
        " style='" + (hidden ? "display:none; " : "") +
        "background:#f7f7f7; border:1px solid #d8d8d8; border-radius:4px; " +
        "font-family:monospace; line-height:1.45; margin-top:14px; max-height:340px; " +
        "min-height:220px; overflow-y:auto; padding:10px; white-space:pre-wrap; word-break:break-word'></div>";
}

function actionErrorHtml() {
    return "<pre class='action-error' style='display:none; background:#fff2f2; " +
        "border:1px solid #d09090; border-radius:4px; margin-top:12px; max-height:180px; " +
        "overflow:auto; padding:10px; white-space:pre-wrap; word-break:break-word'></pre>";
}

function actionResultHtml() {
    return "<pre class='action-result' style='display:none; background:#f3fff2; " +
        "border:1px solid #97c78c; border-radius:4px; margin-top:12px; max-height:180px; " +
        "overflow:auto; padding:10px; white-space:pre-wrap; word-break:break-word'></pre>";
}

function setActionStatus(dialog, text) {
    if (dialog.data("ui-dialog"))
        ensureActionDialogVisible(dialog);

    dialog.find(".action-status").text(actionSummaryText(text));
}

function simpleResultText(result) {
    if (result && typeof result.result === "string")
        return result.result;

    if (typeof result === "string")
        return result;

    try {
        return JSON.stringify(result);
    }
    catch (e) {
        return "Unable to parse server response.";
    }
}

function commentResultFailed(resultText) {
    if (!isDefinedAndFilled(resultText))
        return true;

    return resultText.indexOf("wasn't commented") !== -1 ||
        resultText.indexOf("was not commented") !== -1 ||
        resultText.indexOf("TicketNotFoundException") !== -1 ||
        resultText.indexOf("invalid PR number") !== -1 ||
        resultText.indexOf("Exception happened") !== -1 ||
        resultText.indexOf("not found") !== -1 ||
        resultText.indexOf("API returned an error") !== -1;
}

function showActionError(dialog, text) {
    ensureActionDialogVisible(dialog);
    dialog.find(".action-error").empty().append(linkifiedActionText(boundedActionText(text))).show();
}

function showActionResult(dialog, text) {
    ensureActionDialogVisible(dialog);
    dialog.find(".action-result").empty().append(linkifiedActionText(boundedActionText(text))).show();
}

function linkifiedActionText(text) {
    var value = String(text == null ? "" : text);
    var container = $();
    var urlRe = /(https?:\/\/[^\s;]+)/g;
    var lastIdx = 0;
    var match;

    while ((match = urlRe.exec(value)) !== null) {
        if (match.index > lastIdx)
            container = container.add(document.createTextNode(value.substring(lastIdx, match.index)));

        var url = match[0];

        container = container.add($("<a>", {
            href: url,
            target: "_blank",
            rel: "noopener noreferrer",
            text: url
        })[0]);

        lastIdx = match.index + url.length;
    }

    if (lastIdx < value.length)
        container = container.add(document.createTextNode(value.substring(lastIdx)));

    return container;
}

function actionSummaryText(text) {
    var normalized = String(text == null ? "" : text);
    var githubBody = githubResponseBodyMessage(normalized);

    if (githubBody)
        return githubBody;

    var firstLine = normalized.split(/\r?\n/)[0];
    var maxLen = 260;

    if (firstLine.length <= maxLen)
        return firstLine;

    return firstLine.substring(0, maxLen - 3) + "...";
}

function githubResponseBodyMessage(text) {
    var bodyMatch = text.match(/Response body:\s*(\{[\s\S]*?\})/);

    if (!bodyMatch)
        return null;

    try {
        var body = JSON.parse(bodyMatch[1]);
        var code = body.status || responseCodeFromText(text);

        if (body.message)
            return "GitHub API" + (code ? " " + code : "") + ": " + body.message;
    }
    catch (e) {
        return null;
    }

    return null;
}

function responseCodeFromText(text) {
    var match = text.match(/Response Code\s*:\s*(\d+)/i);

    return match ? match[1] : null;
}

function boundedActionText(text) {
    var normalized = String(text == null ? "" : text);
    var maxLen = 20000;

    if (normalized.length <= maxLen)
        return normalized;

    return normalized.substring(0, maxLen) +
        "\n\n... output truncated in UI (" + normalized.length + " chars total). See server logs for full details.";
}

function actionDialogOptions(title, buttons) {
    return {
        title: title,
        modal: true,
        resizable: false,
        width: Math.max(420, Math.min(780, $(window).width() - 40)),
        minHeight: Math.max(420, Math.min(560, $(window).height() - 60)),
        maxHeight: Math.max(420, $(window).height() - 40),
        buttons: buttons,
        close: function () {
            $(this).dialog("destroy").remove();
            $(".ui-widget-overlay").remove();
        }
    };
}

function commentTargetsLabel(targets) {
    return isDefinedAndFilled(targets) ? targets : "nothing";
}

function commentPolicyLabel(commentOnlyIfNoBlockers) {
    return commentOnlyIfNoBlockers ? "only clean run (no blockers)" : "always";
}

function siteIcon(url) {
    if (!isDefinedAndFilled(url))
        return "";

    try {
        var origin = new URL(url, window.location.href).origin;

        return "<img src='" + escapeHtml(origin + "/favicon.ico") + "' alt='' " +
            "style='display:none; width:14px; height:14px; vertical-align:-2px; margin:0 4px' " +
            "onload='this.style.display=\"inline-block\"' onerror='this.remove()'>";
    }
    catch (e) {
        return "";
    }
}

function buttonLabel(text, leadingIconUrl, trailingIconUrl) {
    return siteIcon(leadingIconUrl) + escapeHtml(text) + siteIcon(trailingIconUrl);
}

function optionalLink(url, label) {
    if (!isDefinedAndFilled(url))
        return "";

    return " <a href='" + escapeHtml(url) + "' target='_blank'>" + escapeHtml(label) + "</a>";
}

function ticketLinkLabel(url, fallback) {
    var normalizedUrl = decodedUrl(url);
    var issueMatch = normalizedUrl.match(/([A-Z][A-Z0-9]+-\d+)(?=$|[/?#&=])/);

    return issueMatch ? issueMatch[1] : fallback;
}

function prLinkLabel(url, fallback) {
    var normalizedUrl = decodedUrl(url);
    var prMatch = normalizedUrl.match(/\/pull\/(\d+)(?=$|[/?#])/);

    return prMatch ? "#" + prMatch[1] : fallback;
}

function decodedUrl(url) {
    try {
        return decodeURIComponent(String(url == null ? "" : url));
    }
    catch (e) {
        return String(url == null ? "" : url);
    }
}

function suitesSummaryHtml(suites) {
    if (!isDefinedAndFilled(suites) || suites.length === 0)
        return "";

    var maxHeight = Math.min(260, Math.max(120, suites.length * 24));

    return suites.length + " suite" + (suites.length === 1 ? "" : "s") + " selected" +
        "<div style='max-height:" + maxHeight + "px; overflow-y:auto; margin-top:6px; padding-left:12px'>" +
        suites.map(escapeHtml).join("<br>") + "</div>";
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

    if (isDefinedAndFilled(suite.suiteId) && isSuiteProblematic(suite) && typeof openAiPromptForSuite === "function") {
        res += " <a href='javascript:void(0);' ";
        res += "onClick='openAiPromptForSuite(decodeURIComponent(";
        res += JSON.stringify(encodeURIComponent(suite.suiteId)) + "))' ";
        res += "title='Open AI prompt with TeamCity context for this suite'>[AI Prompt]</a>";
    }

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
        mInfo += " onClick='" + jsCallAttr("triggerBuilds", [suite.serverId, null, suite.suiteId,
            suite.branchName, false, false, null, prNum, null, false]) + "' ";
        mInfo += " title='trigger build' >queue</a> ";

        mInfo += "<a href='javascript:void(0);' ";
        mInfo += " onClick='" + jsCallAttr("triggerBuilds", [suite.serverId, null, suite.suiteId,
            suite.branchName, true, false, null, prNum, null, false]) + "' ";
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

function isSuiteProblematic(suite) {
    if (isDefinedAndFilled(suite.success) && suite.success === true)
        return false;

    if (isDefinedAndFilled(suite.failedTests) && suite.failedTests > 0)
        return true;

    if (isDefinedAndFilled(suite.result) && suite.result !== "")
        return true;

    if (isDefinedAndFilled(suite.hasCriticalProblem) && suite.hasCriticalProblem)
        return true;

    if (isDefinedAndFilled(suite.blockerComment) && suite.blockerComment !== "")
        return true;

    return false;
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

    if (isDefinedAndFilled(testFail.name) && typeof openAiPromptForTest === "function") {
        res += " <a href='javascript:void(0);' ";
        res += "onClick='openAiPromptForTest(decodeURIComponent(";
        res += JSON.stringify(encodeURIComponent(testFail.name)) + "))' ";
        res += "title='Open AI prompt with TeamCity context for this test'>[AI Prompt]</a>";
    }

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
