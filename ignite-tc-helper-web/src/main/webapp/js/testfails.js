//loadData(); // should be defined by page
//loadStatus element should be provided on page

//@param results - TestFailuresSummary
function showChainOnServersResults(result) {
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
        res += showServerData(server);
    }

    return res;
}


//@param server - see ChainAtServerCurrentStatus
function showServerData(server) {
    var res = "";
    var altTxt = "duration: " + server.durationPrintable;

    res += "<b><a href='" + server.webToHist + "'>" + server.serverName + "</a> ";
    res += "[";
    res += " <a href='" + server.webToBuild + "' title='"+altTxt+"'>"
    res += "tests " + server.failedTests + " suites " + server.failedToFinish + "";
    res += " </a>"
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
    var altTxt = "duration: " + suite.durationPrintable;
    res += "&nbsp; <a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ "+ "<a href='" + suite.webToBuild + "' title='"+altTxt+"'> " + "tests " + suite.failedTests + " " + suite.result + "</a> ]" +
        " " + suite.contactPerson + "";

    if(isDefinedAndFilled(suite.runningBuildCount) && suite.runningBuildCount!=0) {
        res+=" <img src='https://image.flaticon.com/icons/png/128/2/2745.png' width=12px height=12px> ";
        res+=" " + suite.runningBuildCount + " running";
    }
    if(isDefinedAndFilled(suite.queuedBuildCount) && suite.queuedBuildCount!=0) {
        res+=" <img src='https://d30y9cdsu7xlg0.cloudfront.net/png/273613-200.png' width=12px height=12px> ";
        res+="" + suite.queuedBuildCount + " queued";
    }

    if(isDefinedAndFilled(suite.serverId) && isDefinedAndFilled(suite.suiteId) && isDefinedAndFilled(suite.branchName)) {
        res+=" <a href='javascript:void(0);'><img src='https://cdn2.iconfinder.com/data/icons/iconslandplayer/PNG/256x256/CircleBlue/Play1Hot.png'";
        res+=" onClick='triggerBuild(\"" + suite.serverId + "\", \"" + suite.suiteId + "\", \""+suite.branchName+"\")' ";
        res+=" title='trigger build'";
        res+=" width=12px height=12px></a> ";
    }
    res+=" <br>";

    for (var i = 0; i < suite.testFailures.length; i++) {
        res += showTestFailData(suite.testFailures[i]);
    }

    if(typeof suite.webUrlThreadDump !== 'undefined' && suite.webUrlThreadDump!=null) {
        res += "&nbsp; &nbsp; <a href='" + suite.webUrlThreadDump + "'>";
        res += "<img src='https://cdn2.iconfinder.com/data/icons/metro-uinvert-dock/256/Services.png' width=12px height=12px> ";
        res += "Thread Dump</a>"
        res += " <br>";
    }

    res += " <br>";
    return res;
}


//@param testFail - see TestFailure
function showTestFailData(testFail) {
    var res = "";
    res += "&nbsp; &nbsp; ";

    var haveIssue = typeof testFail.webIssueUrl !== 'undefined' && testFail.webIssueUrl!=null
                    && typeof testFail.webIssueText !== 'undefined' && testFail.webIssueText!=null;

    var redSaturation = 255;
    var greenSaturation = 0;
    var blueSaturation = 0;
    var colorCorrect = 0;
    if(testFail.failureRate !== 'undefined' && testFail.failureRate!= null) {
        colorCorrect = parseFloat(testFail.failureRate);
    }
    if(colorCorrect < 50) {
        redSaturation = 255;
        greenSaturation += colorCorrect * 5;
    } else {
        //greenSaturation += 255;
        //redSaturation -= (colorCorrect-50) * 5;

        greenSaturation = 255 -(colorCorrect-50) * 5;;
        redSaturation = 255 - (colorCorrect-50) * 5;
    }
    var color = rgbToHex(redSaturation, greenSaturation, blueSaturation);
    res += " <span style='background-color: " + color + "; width:7px; height:7px; display: inline-block; border-width: 1px; border-color: black; border-style: solid; '></span> ";


    if(haveIssue) {
        res += "<a href='"+testFail.webIssueUrl+"'>";
        res += testFail.webIssueText;
        res += "</a>";
        res += ": ";
    };

    res += testFail.name;

    var haveWeb = typeof testFail.webUrl !== 'undefined' && testFail.webUrl!=null;
    var histContent = "";
    if (testFail.failures != null && testFail.runs != null) {
        histContent += " <span title='" + testFail.failures + " fails / " + testFail.runs + " runs in all tracked branches in helper DB'>";
        if(testFail.failureRate !== 'undefined' && testFail.failureRate!= null )
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

    res += " <br>";
    return res;
}