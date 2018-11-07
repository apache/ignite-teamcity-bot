function showLongRunningTestsSummary(result) {
    var res = "<table>"

    for (var i = 0; i < result.suiteSummaries.length; i++) {
        res += "<tr>";

        res += "<td>" + result.suiteSummaries[i].name + "<td>";

        res += "<td>" + result.suiteSummaries[i].testsCount + "</td>";

        res += "</tr>";
    }

    return res + "</table>";
}