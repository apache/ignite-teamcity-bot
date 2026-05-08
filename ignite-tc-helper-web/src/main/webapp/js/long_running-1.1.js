/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
function showLongRunningTestsSummary(result) {
    var res = "<table>";

    for (var i = 0; i < result.suiteSummaries.length; i++) {
        var summary = result.suiteSummaries[i];

        res += "<tr>";

        res += "<td>";
        res += "<span>" + summary.name + "</span>";
        res += "<span> [avg test duration: " + summary.testAvgTimePrintable + "] </span>";

        res += "<span class='container'>";
        res += " <a href='javascript:void(0);' class='header'>" + more + "</a>";
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
    var res = "<table>";
    for (var i = 0; i < tests.length; i++) {
        res += "<tr>";
        res += "<td>" + tests[i].name;
        res += "</td><td>";
        res += tests[i].timePrintable;
        res += "</td></tr>";
    }
    res += "</table>";

    return res;
}
