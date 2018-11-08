function showLongRunningTestsSummary(result) {
    var res = "<table>"

    for (var i = 0; i < result.suiteSummaries.length; i++) {
        var summary = result.suiteSummaries[i];

        res += "<tr>";

        res += "<td>";
        res += "<span>" + summary.name + "</span>"
        res += "<span> [avg test duration: " + summary.testAvgTimePrintable + "] </span>"

        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>More &gt;&gt;</a>";
        res += "<div class='content'>";
        res += convertLRTestsList(summary.tests);
        res += "</div>";
        res += "</span>";

        res += "</td></tr>";

        res += "<tr><td>&nbsp;</td></tr>";
    }

    setTimeout(initMoreInfo, 100);

    return res + "</table>";
}

function convertLRTestsList(tests) {
    var res = "<table>"
    for (var i = 0; i < tests.length; i++) {
        res += "<tr>";
        res += "<td>" + tests[i].name;
        res += "</td><td>";
        res += tests[i].timePrintable;
        res += "</td></tr>";
    }
    res += "</table>"

    return res;
}
