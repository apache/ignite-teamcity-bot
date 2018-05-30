
//@param results - TestFailuresSummary
//@param settings - Settings (JS class)
function showIssues(result) {
    var res = "";
    res += "Build problems";
        res += "<br>";

    if (!isDefinedAndFilled(result.issues)) {
       return res;
    }

    for (var i = 0; i < result.issues.length; i++) {
        var issue = result.issues[i];

        res += issue.displayType;

        res += " " + issue.issueKey.testOrBuildName;

        if(isDefinedAndFilled(issue.addressNotified)) {
            res += "Notified: [";

             for (var j = 0; j < issue.addressNotified.length; j++) {
                var addressNotified = issue.addressNotified[j];

                res+=addressNotified + ", "
             }
             res+="]";
        }

        res += "<br>";
    }

    return res;
}