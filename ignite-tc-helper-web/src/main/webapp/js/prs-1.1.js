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
function drawTable(srvId, element) {
    let tableId = "serverContributions-" + srvId;

    element.append("<div id='expandAllButton-" + srvId + "' align='right' style='margin-right:50px'></div><br>" +
        "<table id=\"" + tableId + "\" class='ui-widget ui-widget-content'>\n" +
        "            <thead>\n" +
        "            <tr class=\"ui-widget-header \">\n" +
        "                <th>.</th>\n" +
        "                <th>.</th>\n" +
        "                <th>...</th>\n" +
        "                <th>Loading</th>\n" +
        "                <th>...</th>\n" +
        "                <th>.</th>\n" +
        "                <th>.</th>\n" +
        "                <th>.</th>\n" +
        "            </tr>\n" +
        "            </thead>\n" +
        "        </table>\n");
}

function requestTableForServer(srvId, element) {

    let tableId = "serverContributions-" + srvId;

    if ($("#" + tableId).length > 0)
        return; //protection from duplicate

    drawTable(srvId, element);

    $.ajax({
        url: "rest/visa/contributions?serverId=" + srvId,
        success:
            function (result) {
                showContributionsTable(result, srvId, "");
                fillBranchAutocompleteList(result, srvId);
                setAutocompleteFilter();
            }
    });
}

function normalizeDateNum(num) {
    return num < 10 ? '0' + num : num;
}

function showContributionsTable(result, srvId, suiteId) {
    let tableId = 'serverContributions-' + srvId;
    let tableForSrv = $('#' + tableId);

    tableForSrv.dataTable().fnDestroy();

    if (isDefinedAndFilled(result) && result.length > 0)
        $("#expandAllButton-"+ srvId).html("<button class='more green' id='expandAll'>Expand all</button>");

    var table = tableForSrv.DataTable({
        order: [[1, 'desc']],
        data: result,
        "iDisplayLength": 30, //rows to be shown by default
        //"dom": '<lf<t>ip>',
        //"dom": '<"wrapper"flipt>',
        stateSave: true,
        columnDefs: [
            {
                targets: 1,
                className: 'dt-body-center'
            }
        ],
        columns: [
            {
                "className": 'details-control',
                //"orderable":      false,
                "data": null,
                "title": "",
                "defaultContent": "",
                "render": function (data, type, row, meta) {
                    if (type === 'display') {
                        return "<button class='more full green' type='button' id='button_" + row.prNumber +"'>" +
                            "<b>ᴍᴏʀᴇ</b><i class='fas fa-caret-down'></i></button>";
                    }
                }
            },
            {
                "data": "prTimeUpdate",
                title: "Update Time",
                "render": function (data, type, row, meta) {
                    if (type === 'display' && isDefinedAndFilled(data) && data.length >0) {
                        let date = new Date(data);

                        data = normalizeDateNum(date.getFullYear()) + '-' + normalizeDateNum(date.getMonth() + 1) +
                            '-' + normalizeDateNum(date.getDate()) + "<br>" + normalizeDateNum(date.getHours()) +
                            ':' + normalizeDateNum(date.getMinutes()) + ":" + normalizeDateNum(date.getSeconds());
                    }

                    return data;
                }
            },
            {
                "data": "prHtmlUrl",
                title: "PR Number",
                "render": function (data, type, row, meta) {
                    if (type === 'display' && row.prNumber > 0) {
                        data = "<a href='" + data + "'>#" + row.prNumber + "</a>";

                        if (type === 'display' && isDefinedAndFilled(row.prHeadCommit)) {
                            data += " (" + row.prHeadCommit + ")";
                        }
                    }

                    return data;
                }
            }
            , {
                "data": "prTitle",
                title: "Title"
            },
            {
                "data": "prAuthor",
                title: "Author",
                "render": function (data, type, row, meta) {
                    if (type === 'display' && isDefinedAndFilled(row.prAuthorAvatarUrl) && row.prAuthorAvatarUrl.length >0) {
                        data = "<img src='" + row.prAuthorAvatarUrl + "' width='20px' height='20px'> " + data + "";
                    }

                    return data;
                }

            },
            {
                "data": "jiraIssueId",
                title: "JIRA Issue",
                "render": function (data, type, row, meta) {
                    if (type === 'display') {
                        if (data != null && row.jiraIssueUrl != null)
                            data = "<a href='" + row.jiraIssueUrl + "'>" + data + "</a>";
                    }

                    return data;
                }
            },
            {
                "data": "jiraStatusName",
                title: "JIRA Status",
                "render": function (data, type, row, meta) {
                    if (type === 'display') {
                        if (data != null && row.jiraIssueUrl != null)
                            data = "<a href='" + row.jiraIssueUrl + "'>" + data + "</a>";
                    }

                    return data;
                }
            },
            {
                "data": "tcBranchName",
                title: "Resolved Branch Name",
                "render": function (data, type, row, meta) {
                    let prId = data;
                    if (type === 'display' && isDefinedAndFilled(data)) {
                        data = " " + data + "";
                    }

                    return data;
                }
            }
        ]
    });

    $('#expandAll').on('click', function () {
        $('.details-control').click();
    });

    // Add event listener for opening and closing details, enable to only btn   'td.details-control'
    $('#' + tableId + ' tbody').on('click', 'td.details-control', function () {
        var tr = $(this).closest('tr');
        var row = table.row(tr);

        if (row.child.isShown()) {
            // This row is already open - close it
            row.child.hide();
            $("#button_" + row.data().prNumber).html("<b>ᴍᴏʀᴇ</b><i class='fas fa-caret-down'></i>");
            tr.removeClass('shown');
        }
        else {
            // Open this row
            row.child(formatContributionDetails(row.data(), srvId, suiteId)).show();
            $("#button_" + row.data().prNumber).html("<b>ʟᴇss&nbsp;</b><i class='fas fa-caret-up'></i>");
            tr.addClass('shown');
        }
    });
}

function showWaitingResults(stageNum, prId, text) {
    let stageOneStatus = $('#visaStage_' + stageNum + '_' + prId);
    stageOneStatus.css('background', 'darkorange');
    stageOneStatus.attr("title", text);
    stageOneStatus.html("&#9203;");
}

function showStageResult(stageNum, prId, passed, failed) {
    let stageOneStatus = $('#visaStage_' + stageNum + '_' + prId);
    let html;
    if (passed) {
        html = "&#x2714;";
        stageOneStatus.css('background', '#12AD5E');
    } else {
        html = "&#x274C;";
        if(failed)
        stageOneStatus.css('background', 'red');
    }
    stageOneStatus.html(html);
}


function showStageBlockers(stageNum, prId, blockers) {
    let stageOneStatus = $('#visaStage_' + stageNum + '_' + prId);
    let html;
    if (!isDefinedAndFilled(blockers) || blockers == null) {
        html = "?";

        stageOneStatus.css('background', 'darkorange');
    } else if (blockers === 0) {
        html = blockers + " ";
        stageOneStatus.css('background', '#12AD5E');
    } else {
        html = blockers + " ";

        stageOneStatus.css('background', 'red');
    }
    stageOneStatus.html(html);
}


/* Formatting function for row details - modify as you need */
function formatContributionDetails(row, srvId) {
    //  row  is the original data object for the row
    if(!isDefinedAndFilled(row))
        return;

    let prId = row.prNumber;
    var res = "";
    res += "<div class='formgroup'>";
    res += "<table cellpadding='5' cellspacing='0' border='0' style='padding-left:50px;'>\n";
    res += "<tr><td colspan='4' id='choiceOfChain_" + prId + "'></td></tr>";

    //caption of stage
    res += "<tr>\n" +
        "                <td>PR naming</td>\n" +
        "                <td>Build Queued</td>\n" +
        "                <td>Results ready</td>\n" +
        "                <td>JIRA comment</td>\n" +
        //todo  "                <td>Validity check</td>\n" +
        "            </tr>\n";

    //icon of stage
    res += "<tr>\n" +
        "                <th title='PR should have valid naming starting with issue name'><span class='visaStage' id='visaStage_1_" + prId + "'></span></th>\n" +
        "                <th title='Suite should be triggered'><span class='visaStage' id='visaStage_2_" + prId + "'></span></th>\n" +
        "                <th><span class='visaStage' id='visaStage_3_" + prId + "'></span></th>\n" +
         "               <th><span class='visaStage' id='visaStage_4_" + prId + "'></span></th>\n" +
        //todo validityCheck;"                <th><span class='visaStage' id='visaStage_5_" + prId + "'></span></th>\n" +
        "            </tr>\n";

    //action for stage
    res += "        <tr>\n" +
        "            <td></td>\n" +
        "            <td id='triggerBuildFor" + prId + "'>Loading builds...</td>\n" +
        "            <td id='showResultFor" + prId + "'>Loading builds...</td>\n" +
        "            <td id='commentJiraFor" + prId + "'></td>\n" +
        "        </tr>";

    //action row 2
    res += "        <tr>\n" +
        "            <td id='testDraw'></td>\n" +
        "            <td id='triggerAndObserveBuildFor" + prId + "' colspan='3' align='center'></td>\n" +
        "           </tr>";

    //References
    res += "        <tr>\n";

    if (row.prNumber > 0)
        res += "            <td>Edit PR: " + "<a href='" + row.prHtmlUrl + "'>#" + row.prNumber + "</a>" + "</td>\n";
    else
        res += "            <td></td>\n";

    res += "            <td id='viewQueuedBuildsFor" + prId + "'></td>\n" +
        "            <td></td>\n" +
        "            <td></td>\n" +
        "        </tr>";

    res += "    </table>";

    res += "</div>";

    $.ajax({
        url: "rest/visa/contributionStatus" +
            "?serverId=" + srvId +
            "&prId=" + prId,
        success:
            function (result) {
                let selectHtml = "<select id='selectChain_" + prId + "' style='width: 350px'>";

                let isCompleted = [],
                    isIncompleted = [],
                    suites = new Map();

                for (let status of result) {
                    suites.set(status.suiteId, status);

                    if (isDefinedAndFilled(status.branchWithFinishedSuite))
                        isCompleted.push(status);
                    else
                        isIncompleted.push(status);
                }

                for (let status of isCompleted)
                    selectHtml += "<option value='true'>" + status.suiteId + "</option>";

                for (let status of isIncompleted)
                    selectHtml += "<option value='false' style='color:grey'>" + status.suiteId + "</option>";

                selectHtml += "</select>";

                $('#choiceOfChain_' + prId).html(selectHtml);

                prs.set(prId, suites);

                let select = $("#selectChain_" + prId);

                select.change(function () {
                    let pr = prs.get(prId);
                    let selectedOption = $("#selectChain_" + prId + " option:selected").text();
                    let buildIsCompleted = select.val() === 'true';

                    showContributionStatus(pr.get(selectedOption), prId, row, srvId, selectedOption, buildIsCompleted);
                });

                select.change();
            }
    });
    return res;
}

function repaint(srvId) {
    let tableId = 'serverContributions-' + srvId;
    let datatable = $('#' + tableId).DataTable();

    var filteredRows = datatable.rows({filter: 'applied'});
    for (let i = 0; i < filteredRows.length; i++) {
        const rowId = filteredRows[i];

        let row = datatable.row(rowId);

        if (isDefinedAndFilled(row.child)) {
            if (row.child.isShown()) {
                // Replaint this row
                row.child(formatContributionDetails(row.data(), srvId)).show();
            }
        }
    }

    datatable.draw();
}

function repaintLater(srvId) {
    setTimeout(function () {
        repaint(srvId)
    }, 3000);
}

function prShowHref(srvId, suiteId, branchName) {
    return "/pr.html?serverId=" + srvId + "&" +
        "suiteId=" + suiteId +
        //"&baseBranchForTc=" +
        "&branchForTc=" +  branchName +
        "&action=Latest";
}

/**
 *
 * @param status contribution status related to selected run-configuration.
 * @param prId
 * @param row
 * @param srvId
 * @param suiteIdSelected
 */
function showContributionStatus(status, prId, row, srvId, suiteIdSelected) {
    let tdForPr = $('#showResultFor' + prId);

    if (!isDefinedAndFilled(status)) {
        console.log("Status for " + prId + " is undefined. Wait for the Bot to load the suite list.");

        return;
    }

    let buildIsCompleted = isDefinedAndFilled(status.branchWithFinishedSuite);
    let hasJiraIssue = isDefinedAndFilled(row.jiraIssueId);
    let hasQueued = status.queuedBuilds > 0 || status.runningBuilds > 0;
    let queuedStatus = "Has queued builds: " + status.queuedBuilds  + " queued " + " " + status.runningBuilds  + " running";

    let replaintCall = "repaintLater(" +
        "\"" + srvId + "\"" +
        ");";

    var linksToRunningBuilds = "";
    for (let i = 0; i < status.webLinksQueuedSuites.length; i++) {
        const l = status.webLinksQueuedSuites[i];
        linksToRunningBuilds += "<a href=" + l + ">View queued at TC</a> "
    }
    $('#viewQueuedBuildsFor' + prId).html(linksToRunningBuilds);

    if (buildIsCompleted) {
        let finishedBranch = status.branchWithFinishedSuite;

        let reportLink = "<a id='showReportlink_" + prId + "' href='" + prShowHref(srvId, suiteIdSelected, finishedBranch) + "'>" +
            "<button id='show_" + prId + "'>Show " + finishedBranch + " report</button>" +
            "</a>";
        if(isDefinedAndFilled(status.finishedSuiteCommit)) {
            reportLink += "<br>(" + status.finishedSuiteCommit + ")";
        }


        tdForPr.html(reportLink);

        if (hasJiraIssue) {
            let jiraBtn = "<button onclick='" +
                "commentJira(" +
                "\"" + srvId + "\", " +
                "\"" + finishedBranch + "\", " +
                "\"" + suiteIdSelected + "\", " +
                "\"" + row.jiraIssueId + "\"" +
                "); " +
                replaintCall +
                "'";

            if (hasQueued) {
                jiraBtn += " class='disabledbtn' title='" + queuedStatus + "'";
            }
            jiraBtn += ">Comment JIRA</button>";

            $('#commentJiraFor' + prId).html(jiraBtn);
        }
    } else {
        tdForPr.html("No builds, please trigger " + suiteIdSelected);
    }


    showStageResult(1, prId, hasJiraIssue, !hasJiraIssue);

    let buildFinished = isDefinedAndFilled(status.suiteFinished) && status.suiteFinished;
    let noNeedToTrigger = hasQueued || buildIsCompleted;
    showStageResult(2, prId, noNeedToTrigger, false);
    showStageResult(3, prId, buildIsCompleted, false);
    if(hasQueued) {
        showWaitingResults(3, prId, queuedStatus);
    }

    if(isDefinedAndFilled(status.observationsStatus)) {
        showWaitingResults(4, prId, status.observationsStatus);
    }

    function prepareStatusOfTrigger() {
        var res  = "";
        if (hasQueued || buildIsCompleted) {
            res += " class='disabledbtn'";
            if (hasQueued)
                res += " title='" + queuedStatus + "'";
            else
                res += " title='Results are ready. It is still possible to trigger Build'";
        }
        return res;
    }

    if (isDefinedAndFilled(status.resolvedBranch)) {
        var jiraOptional = hasJiraIssue ? row.jiraIssueId : "";
        // triggerBuilds(serverId, suiteIdList, branchName, top, observe, ticketId)  defined in test fails
        let triggerBuildsCall = "triggerBuilds(" +
            "\"" + srvId + "\", " +
            "null, " +
            "\"" + suiteIdSelected + "\", " +
            "\"" + status.resolvedBranch + "\"," +
            " false," +
            " false," +
            "\"" + jiraOptional + "\"); ";
        var res = "<button onClick='" + triggerBuildsCall + replaintCall + "'";
        res += prepareStatusOfTrigger();

        res += ">Trigger build</button>";
        $("#triggerBuildFor" + prId).html(res);
    }

    if (hasJiraIssue && isDefinedAndFilled(status.resolvedBranch)) {
        // triggerBuilds(serverId, suiteIdList, branchName, top, observe, ticketId)  defined in test fails
        let trigObserveCall = "triggerBuilds(" +
            "\"" + srvId + "\", " +
            "null, " +
            "\"" + suiteIdSelected + "\", " +
            "\"" + status.resolvedBranch + "\"," +
            " false," +
            " true," +
            "\"" + jiraOptional + "\"); ";
        var trigAndObs = "<button onClick='" + trigObserveCall + replaintCall + "'";

        trigAndObs += prepareStatusOfTrigger();

        trigAndObs += ">Trigger build and comment JIRA after finish</button>";

        $('#triggerAndObserveBuildFor' + prId).html(trigAndObs);
    }


    $('#testDraw').html(testDraw);

    if(isDefinedAndFilled(status.branchWithFinishedSuite)) {
        $.ajax({
            url: "rest/visa/visaStatus" +
                "?serverId=" + srvId +
                "&suiteId=" + suiteIdSelected +
                "&tcBranch=" + status.branchWithFinishedSuite,
            success:
                function (result) {
                    showStageBlockers(3, prId, result.blockers);
                }
        });
    }
}