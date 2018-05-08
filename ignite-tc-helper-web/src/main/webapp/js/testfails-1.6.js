//loadData(); // should be defined by page
//loadStatus element should be provided on page
//triggerConfirm & triggerDialog element should be provided on page (may be hidden)
var g_initMoreInfoDone = false;

//@param results - TestFailuresSummary
function showChainOnServersResults(result) {
    var minFailRateP = findGetParameter("minFailRate");
    var minFailRate = minFailRateP == null ? 0 : parseFloat(minFailRateP);

    var maxFailRateP = findGetParameter("maxFailRate");
    var maxFailRate = maxFailRateP == null ? 100 : parseFloat(maxFailRateP);
    return showChainResultsWithSettings(result, new Settings(minFailRate, maxFailRate));
}

class Settings {
    constructor(minFailRate, maxFailRate) {
        this.minFailRate = minFailRate;
        this.maxFailRate = maxFailRate;
    }
}

//@param results - TestFailuresSummary
//@param settings - Settings (JS class)
function showChainResultsWithSettings(result, settings) {
    var res = "";
    res += "Chain results";

    if (isDefinedAndFilled(result.failedTests) &&
        isDefinedAndFilled(result.failedToFinish)) {
        res += " [";
        res += "tests " + result.failedTests + " suites " + result.failedToFinish + "";
        res += "]";
    }
    res += "<br>";

    for (var i = 0; i < result.servers.length; i++) {
        var server = result.servers[i];
        res += showChainCurrentStatusData(server, settings);
    }

    setTimeout(initMoreInfo, 100);

    return res;
}


//@param server - see ChainAtServerCurrentStatus
function showChainCurrentStatusData(server, settings) {
    if(isDefinedAndFilled(server.buildNotFound) && server.buildNotFound ) {
        return "<b>Error: Build not found for branch [" + server.branchName + "]</b><br><br>";
    }

    var res = "";
    var altTxt = "";

    if (isDefinedAndFilled(server.durationPrintable))
        altTxt += "duration: " + server.durationPrintable;

    res += "<b><a href='" + server.webToHist + "'>";

    if (isDefinedAndFilled(server.chainName)) {
        res += server.chainName + " ";
    }
    res += server.serverId;

    res += "</a> ";
    res += "[";
    res += " <a href='" + server.webToBuild + "' title='" + altTxt + "'>";
    res += "tests " + server.failedTests + " suites " + server.failedToFinish + "";
    res += " </a>";
    res += "]";
    res += "</b>"

    var mInfo = "";

    var cntFailed = 0;
    var suitesFailedList = "";
    for (var i = 0; i < server.suites.length; i++) {
        var suite = server.suites[i];

        if (!isDefinedAndFilled(suite.suiteId))
            continue;

        //may check failure here in case mode show all

        if (suitesFailedList.length != 0)
            suitesFailedList += ",";

        suitesFailedList += suite.suiteId;
        cntFailed++;
    }

    if (suitesFailedList.length != 0 && isDefinedAndFilled(server.serverId) && isDefinedAndFilled(suite.branchName)) {
        mInfo += "Trigger failed " + cntFailed + " builds";
        mInfo += " <a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuilds(\"" + server.serverId + "\", \"" + suitesFailedList + "\", \"" + suite.branchName + "\", false)' ";
        mInfo += " title='trigger builds'>in queue</a> ";

        mInfo += " <a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuilds(\"" + server.serverId + "\", \"" + suitesFailedList + "\", \"" + suite.branchName + "\", true)' ";
        mInfo += " title='trigger builds'>on top</a><br>";
    }

    mInfo += altTxt + "<br>";

    if (isDefinedAndFilled(server.topLongRunning) && server.topLongRunning.length > 0) {
        mInfo += "Top long running:<br>"

        for (var i = 0; i < server.topLongRunning.length; i++) {
            mInfo += showTestFailData(server.topLongRunning[i], false, settings);
        }
    }


    if (isDefinedAndFilled(server.logConsumers) && server.logConsumers.length > 0) {
        mInfo += "Top Log Consumers:<br>"

        for (var i = 0; i < server.logConsumers.length; i++) {
            mInfo += showTestFailData(server.logConsumers[i], false, settings);
        }
    }

    res += "<span class='container'>";
    res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";
    res += "<div class='content'>";
    res += mInfo + "</div></span>";

    res += "<br><br>";

    for (var i = 0; i < server.suites.length; i++) {
        var suite = server.suites[i];
        res += showSuiteData(suite, settings);
    }

    return res;
}

function triggerBuild(serverId, suiteId, branchName, top) {
    var queueAtTop = isDefinedAndFilled(top) && top;
    $.ajax({
        url: 'rest/build/trigger',
        data: {
            "serverId": serverId,
            "suiteId": suiteId,
            "branchName": branchName,
            "top": queueAtTop
        },
        success: function(result) {
            $("#triggerDialog").html("Trigger builds at server: " + serverId + "<br>" +
                " Suite: " + suiteId + "<br>Branch:" + branchName + "<br>Top: " + top +
                "<br><br> Result: " + result.result);
            $("#triggerDialog").dialog({
                modal: true,
                buttons: {
                    "Ok": function() {
                        $(this).dialog("close");
                    }
                }
            });

            loadData(); // should be defined by page
        },
        error: showErrInLoadStatus
    });
}

function triggerBuilds(serverId, suiteIdList, branchName, top) {
    var res = "Trigger builds at server: " + serverId + "<br>" +
        "Branch:" + branchName + "<br>Top: " + top + "<br>";

    var partsOfStr = suiteIdList.split(',');

    for (var i = 0; i < partsOfStr.length; i++) {
        var suite = partsOfStr[i];
        res += "Suite ID: " + suite + "<br>";
    }
    $("#triggerConfirm").html(res);

    $("#triggerConfirm").dialog({
        modal: true,
        buttons: {
            "Run": function() {
                $(this).dialog("close");

                var queueAtTop = isDefinedAndFilled(top) && top
                $.ajax({
                    url: 'rest/build/triggerBuilds',
                    data: {
                        "serverId": serverId,
                        "suiteIdList": suiteIdList,
                        "branchName": branchName,
                        "top": queueAtTop
                    },
                    success: function(result) {
                        $("#triggerDialog").html("Trigger builds at server: " + serverId + "<br>" +
                            " Suites " + suiteIdList + "<br>Branch:" + branchName + "<br>Top: " + top +
                            "<br><br> Result: " + result.result);
                        $("#triggerDialog").dialog({
                            modal: true,
                            buttons: {
                                "Ok": function() {
                                    $(this).dialog("close");
                                }
                            }
                        });
                        loadData(); // should be defined by page
                    },
                    error: showErrInLoadStatus
                });
            },
            Cancel: function() {
                $(this).dialog("close");
            }
        }
    });
}

//@param suite - see SuiteCurrentStatus
function showSuiteData(suite, settings) {
    var res = "";
    var altTxt = "";
    var moreInfoTxt = "";

    if (isDefinedAndFilled(suite.userCommits) && suite.userCommits != "") {
        moreInfoTxt += "Last commits from: " + suite.userCommits + " <br>";
    }

    moreInfoTxt += "Duration: " + suite.durationPrintable + " <br>";

    altTxt += "Duration: " + suite.durationPrintable + "; ";
    res += "&nbsp; ";

    var failRateText = "";
    if (isDefinedAndFilled(suite.failures) && isDefinedAndFilled(suite.runs) && isDefinedAndFilled(suite.failureRate)) {
        failRateText += "(fail rate " + suite.failureRate + "%)";

        moreInfoTxt += "Recent fails : "+ suite.failureRate +"% [" + suite.failures + " fails / " + suite.runs + " runs]; <br> " ;

        if(isDefinedAndFilled(suite.failsAllHist) && isDefinedAndFilled(suite.failsAllHist.failures)) {
            moreInfoTxt += "All hist fails: "+ suite.failsAllHist.failureRate +"% [" + suite.failsAllHist.failures + " fails / " + suite.failsAllHist.runs + " runs]; <br> " ;
        }

        if(isDefinedAndFilled(suite.criticalFails) && isDefinedAndFilled(suite.criticalFails.failures)) {
            moreInfoTxt += "Critical recent fails: "+ suite.criticalFails.failureRate +"% [" + suite.criticalFails.failures + " fails / " + suite.criticalFails.runs + " runs]; <br> " ;
        }
    }
    var color = failureRateToColor(suite.failureRate);
    res += " <span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='" + failRateText + "'></span> ";

    if (isDefinedAndFilled(suite.latestRuns)) {
        res += drawLatestRuns(suite.latestRuns);
    }

    res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ " + "<a href='" + suite.webToBuild + "' title='" + altTxt + "'> " +
        "tests " + suite.failedTests + " " + suite.result;

    if (isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length > 0) {
        res += " warn " + suite.warnOnly.length;
    }

    res += "</a> ]";


    if (isDefinedAndFilled(suite.contactPerson)) {
        res += " " + suite.contactPerson + "";
    }

    if (isDefinedAndFilled(suite.runningBuildCount) && suite.runningBuildCount != 0) {
        res += " <img src='https://image.flaticon.com/icons/png/128/2/2745.png' width=12px height=12px> ";
        res += " " + suite.runningBuildCount + " running";
    }
    if (isDefinedAndFilled(suite.queuedBuildCount) && suite.queuedBuildCount != 0) {
        res += " <img src='https://d30y9cdsu7xlg0.cloudfront.net/png/273613-200.png' width=12px height=12px> ";
        res += "" + suite.queuedBuildCount + " queued";
    }

    var mInfo = "";
    if (isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
        mInfo += " Trigger build: ";
        mInfo += "<a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \"" + suite.branchName + "\", false)' ";
        mInfo += " title='trigger build' >queue</a> ";

        mInfo += "<a href='javascript:void(0);' ";
        mInfo += " onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \"" + suite.branchName + "\", true)' ";
        mInfo += " title='trigger build at top of queue'>top</a><br>";
    }

    mInfo += moreInfoTxt;

    if (isDefinedAndFilled(suite.topLongRunning) && suite.topLongRunning.length > 0) {
        mInfo += "Top long running:<br>"

        for (var i = 0; i < suite.topLongRunning.length; i++) {
            mInfo += showTestFailData(suite.topLongRunning[i], false, settings);
        }
    }

    if (isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length > 0) {
        mInfo += "Warn Only:<br>"

        for (var i = 0; i < suite.warnOnly.length; i++) {
            mInfo += showTestFailData(suite.warnOnly[i], false, settings);
        }
    }

    if (isDefinedAndFilled(suite.logConsumers) && suite.logConsumers.length > 0) {
        mInfo += "Top Log Consumers:<br>"

        for (var i = 0; i < suite.logConsumers.length; i++) {
            mInfo += showTestFailData(suite.logConsumers[i], false, settings);
        }
    }

    res += "<span class='container'>";
    res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";
    res += "<div class='content'>";
    res += mInfo + "</div></span>";

    res += " <br>";

    for (var i = 0; i < suite.testFailures.length; i++) {
        res += showTestFailData(suite.testFailures[i], true, settings);
    }

    if (isDefinedAndFilled(suite.webUrlThreadDump)) {
        res += "&nbsp; &nbsp; <a href='" + suite.webUrlThreadDump + "'>";
        res += "<img src='https://cdn2.iconfinder.com/data/icons/metro-uinvert-dock/256/Services.png' width=12px height=12px> ";
        res += "Thread Dump</a>";
        res += " <br>";
    }

    res += " <br>";
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
        greenSaturation = 255 - (colorCorrect - 50) * 5;;
        redSaturation = 255 - (colorCorrect - 50) * 5;
    }
    return rgbToHex(redSaturation, greenSaturation, blueSaturation);
}


//@param testFail - see TestFailure
function showTestFailData(testFail, isFailureShown, settings) {

    if (isDefinedAndFilled(testFail.failureRate) &&
        isFailureShown) {
        if (parseFloat(testFail.failureRate) < settings.minFailRate)
            return ""; //test is hidden

        if (parseFloat(testFail.failureRate) > settings.maxFailRate)
            return ""; //test is hidden
    }

    var res = "";
    res += "&nbsp; &nbsp; ";

    var haveIssue = isDefinedAndFilled(testFail.webIssueUrl) && isDefinedAndFilled(testFail.webIssueText)

    var color = isFailureShown ? failureRateToColor(testFail.failureRate) : "white";

    var investigated = isDefinedAndFilled(testFail.investigated) && testFail.investigated;
    if (investigated) {
        res += "<img src='https://d30y9cdsu7xlg0.cloudfront.net/png/324212-200.png' width=11px height=11px> ";
        res += "<span style='opacity: 0.75'> ";
    }

    var bold = false;
    if (isFailureShown) {
        var altForWarn = "";
        if (!isDefinedAndFilled(testFail.failureRate) || !isDefinedAndFilled(testFail.runs)) {
            altForWarn = "No fail rate info, probably new failure or suite critical failure";
        } else if (testFail.failures < 2) {
            altForWarn = "Test failures count is low < 2, probably new test introduced";
        } else if (testFail.runs < 10) {
            altForWarn = "Test runs count is low < 10, probably new test introduced";
        }

        if (altForWarn != "") {
            res += "<img src='https://image.flaticon.com/icons/svg/159/159469.svg' width=11px height=11px title='" + altForWarn + "' > ";
            bold = true;
            res += "<b>";
        }
    }

    res += " <span style='background-color: " + color + "; width:8px; height:8px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";

    if (isDefinedAndFilled(testFail.latestRuns)) {
        res += drawLatestRuns(testFail.latestRuns);
    }

    if (isDefinedAndFilled(testFail.curFailures) && testFail.curFailures > 1)
        res += "[" + testFail.curFailures + "] ";

    if (haveIssue) {
        res += "<a href='" + testFail.webIssueUrl + "'>";
        res += testFail.webIssueText;
        res += "</a>";
        res += ": ";
    };

    if (isDefinedAndFilled(testFail.suiteName) && isDefinedAndFilled(testFail.testName))
        res += "<font color='grey'>" + testFail.suiteName + ":</font> " + testFail.testName;
    else
        res += testFail.name;

    var haveWeb = isDefinedAndFilled(testFail.webUrl);
    var histContent = "";
    if (isFailureShown && testFail.failures != null && testFail.runs != null) {
        var testFailTitle = "recent rate: " + testFail.failures + " fails / " + testFail.runs + " runs" ;

        if(isDefinedAndFilled(testFail.failsAllHist) && isDefinedAndFilled(testFail.failsAllHist.failures)) {
            testFailTitle +=
                 "; all history: " + testFail.failsAllHist.failureRate + "% ["+
                  testFail.failsAllHist.failures + " fails / " +
                  testFail.failsAllHist.runs + " runs] " ;
        }

        histContent += " <span title='" +testFailTitle + "'>";
        if (isDefinedAndFilled(testFail.failureRate))
            histContent += "(fail rate " + testFail.failureRate + "%)";
        else
            histContent += "(fails: " + testFail.failures + "/" + testFail.runs + ")";
        histContent += "</span>";

    } else if (haveWeb) {
        histContent += " (test history)";
    }
    if (haveWeb)
        res += "<a href='" + testFail.webUrl + "'>";
    res += histContent;
    if (haveWeb)
        res += "</a>";

    if (!isFailureShown && isDefinedAndFilled(testFail.durationPrintable))
        res += " duration " + testFail.durationPrintable;

    if (bold)
        res += "</b>";

    if (investigated)
        res += "</span> ";


    if (isDefinedAndFilled(testFail.warnings) && testFail.warnings.length > 0) {
        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";

        res += "<div class='content'>";

        res += "<p class='logMsg'>"
        for (var i = 0; i < testFail.warnings.length; i++) {
            res + "&nbsp; &nbsp; ";
            res + "&nbsp; &nbsp; ";
            res += testFail.warnings[i];
            res += " <br>";
        }
        res += "</p>"

        res += "</div></span>";

    }

    res += " <br>";

    return res;
}

function drawLatestRuns(latestRuns) {
    var res = "";
    res += "<span title='Latest master runs history from right to left is oldest to newest. Red-failed,green-passed,black-timeout'>";

    // res += "<span style='background-color: white; width:" + ((50-latestRuns.length) * 2) + "px; height:10px; display: inline-block;'></span>";

    var len = 1;
    var prevState = null;
    for (var i = 0; i < latestRuns.length; i++) {
        var runCode = latestRuns[i];

        if (prevState == null) {
            //skip
        } else if (prevState == runCode) {
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
    res += "</span> ";

    return res;
}

function drawLatestRunsBlock(state, len) {
    var runColor = "white";

    if (state == 0)
        runColor = "green";
    else if (state == 1)
        runColor = "red";
    else if (state == 2)
        runColor = "grey";
    else if (state == 3)
        runColor = "black";

    return "<span style='background-color: " + runColor + "; width:" + (len * 2) + "px; height:10px; display: inline-block;'></span>";
}


function initMoreInfo() {
    $(".header").unbind("click");
    $(".header").click(function() {
        $header = $(this);
        //getting the next element
        $content = $header.next();
        //open up the content needed - toggle the slide- if visible, slide up, if not slidedown.
        $content.slideToggle(500, function() {
            //execute this after slideToggle is done
            //change text of header based on visibility of content div
            $header.text(function() {
                //change text based on condition
                return $content.is(":visible") ? "Hide <<" : "More >>";
            });
        });

    });
}