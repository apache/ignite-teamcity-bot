//loadData(); // should be defined by page
//loadStatus element should be provided on page

//@param results - TestFailuresSummary
var g_lastJson = ""
var g_lastHtml = ""

function showChainOnServersResults(result) {
     if(result==g_lastJson)
        return g_lastHtml;
        
     var res = "";
     res += "Chain results";
     if(isDefinedAndFilled(result.failedTests) &&
        isDefinedAndFilled(result.failedToFinish)) {
        res += " [";
        res += "tests " + result.failedTests + " suites " + result.failedToFinish + "";
        res += "]";
    }
    res += "<br>";

    for (var i = 0; i < result.servers.length; i++) {
        var server = result.servers[i];
        res += showChainAtServerData(server);
    }

    setTimeout(initMoreInfo, 100);

    g_lastJson=result;
    g_lastHtml=res;

    return res;
}


//@param server - see ChainAtServerCurrentStatus
function showChainAtServerData(server) {
    var res = "";
    var altTxt = "";

    if(isDefinedAndFilled(server.durationPrintable))
        altTxt+="duration: " + server.durationPrintable;

    res += "<b><a href='" + server.webToHist + "'>";

    if(isDefinedAndFilled(server.chainName)) {
        res+=server.chainName + " ";
    }
    res += server.serverName;

    res += "</a> ";
    res += "[";
    res += " <a href='" + server.webToBuild + "' title='"+altTxt+"'>";
    res += "tests " + server.failedTests + " suites " + server.failedToFinish + "";
    res += " </a>";
    res += "]";
    res += "</b><br><br>";

    var arrayLength = server.suites.length;
    for (var i = 0; i < arrayLength; i++) {
        var suite = server.suites[i];
        res += showSuiteData(suite);
    }

    return res;
}

function triggerBuild(serverId, suiteId, branchName) {
     $.ajax({
            url: 'rest/build/trigger',
            data: {"serverId": serverId, "suiteId":suiteId, "branchName":branchName},
            success: function(result) {
                alert("Triggered build " + serverId +" ," + suiteId + " ," + branchName + ": " + result.result);
                loadData(); // should be defined by page
            },
            error: showErrInLoadStatus
        });
}

//@param suite - see SuiteCurrentStatus
function showSuiteData(suite) {
    var res = "";
    var altTxt = "";

    if(isDefinedAndFilled(suite.userCommits) && suite.userCommits!="") {
        altTxt+="Last commits from: " + suite.userCommits + " <br>";
    }

    altTxt+= "Duration: " + suite.durationPrintable + " <br>";
    res += "&nbsp; ";

    var failRateText="";
    if (isDefinedAndFilled(suite.failures) && isDefinedAndFilled(suite.runs)  && isDefinedAndFilled(suite.failureRate)) {
        altTxt += "Stat: " + suite.failures + " fails / " + suite.runs + " runs in all tracked branches in helper DB";
        failRateText += "(fail rate " + suite.failureRate + "%)";
        altTxt +="; " + failRateText + " <br>   ";
    }
    var color = failureRateToColor(suite.failureRate);
    res += " <span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='"+failRateText+"'></span> ";


    res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ "+ "<a href='" + suite.webToBuild + "' title='"+altTxt+"'> " + "tests " + suite.failedTests + " " + suite.result + "</a> ]";


    if(isDefinedAndFilled(suite.contactPerson)) {
        res += " " + suite.contactPerson + "";
    }

    if(isDefinedAndFilled(suite.runningBuildCount) && suite.runningBuildCount!=0) {
        res+=" <img src='https://image.flaticon.com/icons/png/128/2/2745.png' width=12px height=12px> ";
        res+=" " + suite.runningBuildCount + " running";
    }
    if(isDefinedAndFilled(suite.queuedBuildCount) && suite.queuedBuildCount!=0) {
        res+=" <img src='https://d30y9cdsu7xlg0.cloudfront.net/png/273613-200.png' width=12px height=12px> ";
        res+="" + suite.queuedBuildCount + " queued";
    }


    res+= "<span class='container'>";
    res+= " <a href='javascript:void(0);' class='header'>More info &gt;&gt;</a>";

    res+= "<div class='content'>";
    if(isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
        res+=" <a href='javascript:void(0);' ";
        res+=" onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \""+suite.branchName+"\")' ";
        res+=" title='trigger build'";
        res+=" >trigger build</a><br>";
    }

    res+= altTxt;

    if(isDefinedAndFilled(suite.topLongRunning) && suite.topLongRunning.length>0) {
        res+="Top long running:<br>"

        for (var i = 0; i < suite.topLongRunning.length; i++) {
            res += showTestFailData(suite.topLongRunning[i]);
        } 
    }

    res+= "</div></span>";

    res+=" <br>";

    for (var i = 0; i < suite.testFailures.length; i++) {
        res += showTestFailData(suite.testFailures[i]);
    }

    if(isDefinedAndFilled(suite.webUrlThreadDump)) {
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
    if(isDefinedAndFilled(failureRate)) {
        colorCorrect = parseFloat(failureRate);
    }

    if(colorCorrect < 50) {
        redSaturation = 255;
        greenSaturation += colorCorrect * 5;
    } else {
        greenSaturation = 255 -(colorCorrect-50) * 5;;
        redSaturation = 255 - (colorCorrect-50) * 5;
    }
   return rgbToHex(redSaturation, greenSaturation, blueSaturation);
}


//@param testFail - see TestFailure
function showTestFailData(testFail) {
    var res = "";
    res += "&nbsp; &nbsp; ";

    var haveIssue = isDefinedAndFilled(testFail.webIssueUrl) && isDefinedAndFilled(testFail.webIssueText)

    var color = failureRateToColor(testFail.failureRate);

    var investigated = isDefinedAndFilled(testFail.investigated) && testFail.investigated;
    if(investigated) {
        res += "<img src='https://d30y9cdsu7xlg0.cloudfront.net/png/324212-200.png' width=8px height=8px> ";
        res += "<span style='opacity: 0.75'> ";
    }
    res += " <span style='background-color: " + color + "; width:7px; height:7px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";

    if(isDefinedAndFilled(testFail.curFailures) && testFail.curFailures>1)
        res+= "[" + testFail.curFailures + "] ";

    if(haveIssue) {
        res += "<a href='"+testFail.webIssueUrl+"'>";
        res += testFail.webIssueText;
        res += "</a>";
        res += ": ";
    };

    if(isDefinedAndFilled(testFail.suiteName) && isDefinedAndFilled(testFail.testName))
        res += "<font color='grey'>" + testFail.suiteName + ":</font> " + testFail.testName ;
    else
        res += testFail.name;

    var haveWeb = isDefinedAndFilled(testFail.webUrl);
    var histContent = "";
    if (testFail.failures != null && testFail.runs != null) {
        histContent += " <span title='" + testFail.failures + " fails / " + testFail.runs + " runs in all tracked branches in helper DB'>";
        if(isDefinedAndFilled(testFail.failureRate))
            histContent += "(fail rate " + testFail.failureRate + "%)";
        else
            histContent += "(fails: " + testFail.failures + "/" + testFail.runs + ")";
        histContent += "</span>";

    } else if(haveWeb) {
        histContent += " (test history)";
    }
    if (haveWeb)
        res += "<a href='"+testFail.webUrl+"'>";
    res += histContent;
    if (haveWeb)
        res += "</a>";

    if(isDefinedAndFilled(testFail.durationPrintable))
        res += " duration " +testFail.durationPrintable;

    if(investigated)
        res += "</span> ";

    res += " <br>";
    return res;
}

function initMoreInfo() {
$(".header").click(function () {
    $header = $(this);
    //getting the next element
    $content = $header.next();
    //open up the content needed - toggle the slide- if visible, slide up, if not slidedown.
    $content.slideToggle(500, function () {
        //execute this after slideToggle is done
        //change text of header based on visibility of content div
        $header.text(function () {
            //change text based on condition
            return $content.is(":visible") ? "Hide <<" : "More info >>";
        });
    });

});
}