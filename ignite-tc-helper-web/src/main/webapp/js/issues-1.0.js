
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

        res += issue.displayType + " " + issue.objectId;
        res += "<br>";
    }

    return res;
}