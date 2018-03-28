//loadData(); // should be defined by page
//loadStatus element should be provided on page
//@param results - TestFailuresSummary

var g_initMoreInfoDone = false;

function showChainOnServersResults(result) {
    var minFailRateP= findGetParameter("minFailRate");
    var minFailRate = minFailRateP==null ? 0 : parseFloat(minFailRateP);

    var maxFailRateP= findGetParameter("maxFailRate");
    var maxFailRate = maxFailRateP==null ? 100 : parseFloat(maxFailRateP);
    return showChainResultsWithSettings(result, new Settings(minFailRate, maxFailRate));
}

class Settings {
  constructor(minFailRate, maxFailRate) {
    this.minFailRate = minFailRate;
    this.maxFailRate = maxFailRate;
  }
}

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
        res += showChainAtServerData(server, settings);
    }

    setTimeout(initMoreInfo, 100);

    return res;
}


//@param server - see ChainAtServerCurrentStatus
function showChainAtServerData(server, settings) {
    var res = "";
    var altTxt = "";

    if (isDefinedAndFilled(server.durationPrintable))
        altTxt += "duration: " + server.durationPrintable;

    res += "<b><a href='" + server.webToHist + "'>";

    if (isDefinedAndFilled(server.chainName)) {
        res += server.chainName + " ";
    }
    res += server.serverName;

    res += "</a> ";
    res += "[";
    res += " <a href='" + server.webToBuild + "' title='" + altTxt + "'>";
    res += "tests " + server.failedTests + " suites " + server.failedToFinish + "";
    res += " </a>";
    res += "]";
    res += "</b><br><br>";

    var arrayLength = server.suites.length;
    for (var i = 0; i < arrayLength; i++) {
        var suite = server.suites[i];
        res += showSuiteData(suite, settings);
    }

    return res;
}

function triggerBuild(serverId, suiteId, branchName) {
    $.ajax({
        url: 'rest/build/trigger',
        data: {
            "serverId": serverId,
            "suiteId": suiteId,
            "branchName": branchName
        },
        success: function(result) {
            alert("Triggered build " + serverId + " ," + suiteId + " ," + branchName + ": " + result.result);
            loadData(); // should be defined by page
        },
        error: showErrInLoadStatus
    });
}

//@param suite - see SuiteCurrentStatus
function showSuiteData(suite, settings) {
    var res = "";
    var altTxt = "";

    if (isDefinedAndFilled(suite.userCommits) && suite.userCommits != "") {
        altTxt += "Last commits from: " + suite.userCommits + " <br>";
    }

    altTxt += "Duration: " + suite.durationPrintable + " <br>";
    res += "&nbsp; ";

    var failRateText = "";
    if (isDefinedAndFilled(suite.failures) && isDefinedAndFilled(suite.runs) && isDefinedAndFilled(suite.failureRate)) {
        altTxt += "Stat: " + suite.failures + " fails / " + suite.runs + " runs in all tracked branches in helper DB";
        failRateText += "(master fail rate " + suite.failureRate + "%)";
        altTxt += "; " + failRateText + " <br>   ";
    }
    var color = failureRateToColor(suite.failureRate);
    res += " <span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='" + failRateText + "'></span> ";


    res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ " + "<a href='" + suite.webToBuild + "' title='" + altTxt + "'> "
         + "tests " + suite.failedTests + " " + suite.result;

    if(isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length>0) {
        res+=" warn " + suite.warnOnly.length ;
    }

    res+= "</a> ]";


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


    res += "<span class='container'>";
    res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";

    res += "<div class='content'>";
    if (isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
        res += " <a href='javascript:void(0);' ";
        res += " onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \"" + suite.branchName + "\")' ";
        res += " title='trigger build'";
        res += " >trigger build</a><br>";
    }

    res += altTxt;

    if (isDefinedAndFilled(suite.topLongRunning) && suite.topLongRunning.length > 0) {
        res += "Top long running:<br>"

        for (var i = 0; i < suite.topLongRunning.length; i++) {
            res += showTestFailData(suite.topLongRunning[i], false, settings);
        }
    }

    if (isDefinedAndFilled(suite.warnOnly) && suite.warnOnly.length > 0) {
            res += "Warn Only:<br>"

            for (var i = 0; i < suite.warnOnly.length; i++) {
                res += showTestFailData(suite.warnOnly[i], false, settings);
            }
    }

    res += "</div></span>";

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

    if (isDefinedAndFilled(testFail.failureRate)
        && isFailureShown) {
        if (parseFloat(testFail.failureRate) < settings.minFailRate)
            return ""; //test is hidden

        if (parseFloat(testFail.failureRate) > settings.maxFailRate)
            return ""; //test is hidden
    }

    var res = "";
    res += "&nbsp; &nbsp; ";

    var haveIssue = isDefinedAndFilled(testFail.webIssueUrl) && isDefinedAndFilled(testFail.webIssueText)

    var color = failureRateToColor(testFail.failureRate);

    var investigated = isDefinedAndFilled(testFail.investigated) && testFail.investigated;
    if (investigated) {
        res += "<img src='https://d30y9cdsu7xlg0.cloudfront.net/png/324212-200.png' width=11px height=11px> ";
        res += "<span style='opacity: 0.75'> ";
    }

    var bold = false;
    if(isFailureShown) {
        var altForWarn = "";
        if(!isDefinedAndFilled(testFail.failureRate) || !isDefinedAndFilled(testFail.runs)) {
            altForWarn = "No fail rate info, probably new failure or suite critical failure";
       // } else if(parseFloat(testFail.failureRate) < 1) {
       //     altForWarn = "Test fail rate less than 1%, probably new failure";
        }  else if(testFail.failures < 3) {
            altForWarn = "Test failures count is low < 3, probably new test introduced";
        }  else if(testFail.runs < 10) {
            altForWarn = "Test runs count is low < 10, probably new test introduced";
        }

        if(altForWarn!="") {
            res += "<img src='https://image.flaticon.com/icons/svg/159/159469.svg' width=11px height=11px title='"+altForWarn+"' > ";
            bold = true;
            res += "<b>";
        }
    }

    res += " <span style='background-color: " + color + "; width:7px; height:7px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";

    if(isDefinedAndFilled(testFail.latestRuns)) {
          res += " <span title='Latest runs history'>";
          for (var i = 0; i < testFail.latestRuns.length; i++) {

                    var runCode = testFail.latestRuns[i];
                    var runColor = "white";
                    if(runCode ==0)
                        runColor = "green";
                    else if(runCode == 1)
                        runColor = "red";
                    else if(runCode == 2)
                        runColor = "grey";

                    res += "<span style='background-color: " + runColor + "; width:2px; height:9px; display: inline-block; border-width: 0px; border-color: black; border-style: solid;'></span>";

          }
          res += "</span> "
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
        histContent += " <span title='" + testFail.failures + " fails / " + testFail.runs + " runs in all tracked branches in helper DB'>";
        if (isDefinedAndFilled(testFail.failureRate))
            histContent += "(master fail rate " + testFail.failureRate + "%)";
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

    if(bold)
        res += "</b>";

    if (investigated)
        res += "</span> ";


    if(isDefinedAndFilled(testFail.warnings) && testFail.warnings.length>0) {
        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";

        res += "<div class='content'>";

        res+="<p class='logMsg'>"
            for (var i = 0; i < testFail.warnings.length; i++) {
                res+"&nbsp; &nbsp; ";
                res+"&nbsp; &nbsp; ";
                res +=  testFail.warnings[i];
                res += " <br>";
            }
        res+="</p>"

        res += "</div></span>";

    }


    res += " <br>";

    return res;
}

function initMoreInfo() {
    $(".header").unbind( "click" );
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