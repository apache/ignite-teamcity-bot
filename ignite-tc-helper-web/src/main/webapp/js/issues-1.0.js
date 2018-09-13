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

        var color = 'red';
        var issueTitle = '';
        res += " <span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;' title='" + issueTitle + "'></span> ";

        res += issue.displayType;

        res += " " + issue.issueKey.testOrBuildName;

        if (isDefinedAndFilled(issue.addressNotified)) {
            res += " Users Notified: [";

            for (var j = 0; j < issue.addressNotified.length; j++) {
                var addressNotified = issue.addressNotified[j];

                res += addressNotified + ", "
            }
            res += "]";
        }

        res += "<br><br>";
    }

    return res;
}