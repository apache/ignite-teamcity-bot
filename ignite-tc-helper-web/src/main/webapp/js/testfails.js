
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

function showSuiteData(suite) {
    var res = "";
    var altTxt = "duration: " + suite.durationPrintable;
    res += "<a href='" + suite.webToHist + "'>" + suite.name + "</a> " +
        "[ "+ "<a href='" + suite.webToBuild + "' title='"+altTxt+"'> " + "tests " + suite.failedTests + " " + suite.result + "</a> ]" +
        " " + suite.contactPerson + "" +
        " <br>";

    for (var i = 0; i < suite.testFailures.length; i++) {
        var testFail = suite.testFailures[i];
        res += showTestFailData(testFail);
    }

    if(typeof suite.webUrlThreadDump !== 'undefined' && suite.webUrlThreadDump!=null) {
        res += "&nbsp; <a href='" + suite.webUrlThreadDump + "'>";
        res += "<img src='https://cdn2.iconfinder.com/data/icons/metro-uinvert-dock/256/Services.png' width=12px height=12px> ";
        res += "Thread Dump</a>"
        res += " <br>";
    }

    res += " <br>";
    return res;
}

function showTestFailData(testFail) {
    var res = "";
    res += "&nbsp; ";

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