function drawTable(srvId, suiteId, element) {

    element.append("<table id=\"serverContributions-" +
        srvId + "\" class=\"ui-widget ui-widget-content\">\n" +
        "            <thead>\n" +
        "            <tr class=\"ui-widget-header \">\n" +
        "                <th>.</th>\n" +
        "                <th>...</th>\n" +
        "                <th>Loading</th>\n" +
        "                <th>...</th>\n" +
        "                <th>.</th>\n" +
        "                <th>.</th>\n" +
        "            </tr>\n" +
        "            </thead>\n" +
        "        </table>\n");
}

function requestTableForServer(srvId, suiteId, element) {
    // TODO multiple servers
    if (srvId != "apache")
        return;

    drawTable(srvId, suiteId, element);

    $.ajax({
        url: "rest/visa/contributions?serverId=" + srvId,
        success:
            function (result) {
                showContributionsTable(result, srvId, suiteId)
            }
    });
}

function showContributionsTable(result, srvId, suiteId) {
    let tableId = 'serverContributions-' + srvId;
    let tableForSrv = $('#' + tableId);

    tableForSrv.dataTable().fnDestroy();

    var table = tableForSrv.DataTable({
        data: result,
        "iDisplayLength": 30, //rows to be shown by default
        //"dom": '<lf<t>ip>',
        //"dom": '<"wrapper"flipt>',
        stateSave: true,
        columns: [
            {
                "className": 'details-control',
                //"orderable":      false,
                "data": null,
                "title": "",
                "defaultContent": "",
                "render": function (data, type, row, meta) {
                    if (type === 'display') {
                        return "<button>&#x2714; Inspect</button>";
                    }
                }
            },
            {
                "data": "prHtmlUrl",
                title: "PR Number",
                "render": function (data, type, row, meta) {
                    if (type === 'display') {
                        data = "<a href='" + data + "'>#" + row.prNumber + "</a>";
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
                    if (type === 'display') {
                        data = "<img src='" + row.prAuthorAvatarUrl + "' width='20px' height='20px'> " + data + "";
                    }

                    return data;
                }

            },
            {
                "data": "jiraIssueId",
                title: "JIRA Issue"
            },
            {
                "data": "tcBranchName",
                title: "Resolved Branch Name",
                "render": function (data, type, row, meta) {
                    let prId = data;
                    if (type === 'display' && isDefinedAndFilled(data)) {
                        data = "<a id='link_" + prId + "' href='" +
                            prShowHref(srvId, suiteId, data) +
                            "'>" +
                            "<button id='show_" + prId + "'>Open " + data + "head</button></a>";
                    }

                    return data;
                }
            }
        ]
    });

    // Add event listener for opening and closing details, enable to only btn   'td.details-control'
    $('#' + tableId + ' tbody').on('click', 'td', function () {
        var tr = $(this).closest('tr');
        var row = table.row(tr);

        if (row.child.isShown()) {
            // This row is already open - close it
            row.child.hide();
            tr.removeClass('shown');
        }
        else {
            // Open this row
            row.child(formatContributionDetails(row.data(), srvId, suiteId)).show();
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


/* Formatting function for row details - modify as you need */
function formatContributionDetails(row, srvId, suiteId) {
    //  row  is the original data object for the row
    if(!isDefinedAndFilled(row))
        return;

    let prId = row.prNumber;
    var res = "";
    res += "<div class='formgroup'>";
    res += "<table cellpadding='5' cellspacing='0' border='0' style='padding-left:50px;'>\n";

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
        "                <th title='Run All should be triggered'><span class='visaStage' id='visaStage_2_" + prId + "'></span></th>\n" +
        "                <th><span class='visaStage' id='visaStage_3_" + prId + "'></span></th>\n" +
         "               <th><span class='visaStage' id='visaStage_4_" + prId + "'></span></th>\n" +
        //todo validityCheck;"                <th><span class='visaStage' id='visaStage_5_" + prId + "'></span></th>\n" +
        "            </tr>\n";

    //action for stage
    res += "        <tr>\n" +
        "            <td>Edit PR: " + "<a href='" + row.prHtmlUrl + "'>#" + row.prNumber + "</a>" + "</td>\n" +
        "               <td id='triggerBuildFor" + prId + "'>Loading builds...</td>\n" +
        "               <td id='showResultFor" + prId + "'>Loading builds...</td>\n" +
        "               <td id='commentJiraFor" + prId + "'></td>\n" +
        "        </tr>";

    //action row 2
    res += "        <tr>\n" +
        "            <td></td>\n" +
        "            <td id='triggerAndObserveBuildFor" + prId + "' colspan='3' align='center'>d</td>\n" +
        "           </tr>";

    res += "    </table>";

    res += "</div>";


    $.ajax({
        url: "rest/visa/contributionStatus" +
            "?serverId=" + srvId +
            "&suiteId=" + suiteId +
            "&prId=" + prId,
        success:
            function (result) {
                showContributionStatus(result, prId, row, srvId, suiteId);
            }
    });
    return res;
}


function showContributionStatus(status, prId, row, srvId, suiteId) {
    let finishedBranch = status.branchWithFinishedRunAll;
    let tdForPr = $('#showResultFor' + prId);
    let buildIsCompleted = isDefinedAndFilled(finishedBranch);
    let hasJiraIssue = isDefinedAndFilled(row.jiraIssueId);
    let hasQueued = status.queuedBuilds > 0 || status.runningBuilds > 0;
    if (buildIsCompleted) {
        tdForPr.html("<a id='link_" + prId + "' href='" + prShowHref(srvId, suiteId, finishedBranch) + "'>" +
            "<button id='show_" + prId + "'>Show " + finishedBranch + " report</button></a>");

        if (hasJiraIssue) {
            let jiraBtn = "<button onclick='" +
                "commentJira(" +
                "\"" + srvId + "\", " +
                "\"" + suiteId + "\", " +
                "\"" + finishedBranch + "\", " +
                "\"" + row.jiraIssueId + "\"" +
                ")'";

            if (hasQueued) {
                jiraBtn += " class='disabledbtn' title='Has queued builds'";
            }
            jiraBtn += ">Comment JIRA</button>";

            $('#commentJiraFor' + prId).html(jiraBtn);
        }
    } else {
        tdForPr.html("No builds, please trigger " + suiteId);
    }


    showStageResult(1, prId, hasJiraIssue, !hasJiraIssue);
    let noNeedToTrigger = hasQueued || buildIsCompleted;
    showStageResult(2, prId, noNeedToTrigger, false);
    showStageResult(3, prId, buildIsCompleted, false);
    if(hasQueued) {
        showWaitingResults(3, prId, "Has queued builds: " + status.queuedBuilds  + " queued " + " ");

    }

    if(isDefinedAndFilled(status.observationsStatus)) {
        showWaitingResults(4, prId, status.observationsStatus);
    }

    if (isDefinedAndFilled(status.resolvedBranch)) {
        var jiraOptional = hasJiraIssue ? row.jiraIssueId : "";
        // triggerBuilds(serverId, suiteIdList, branchName, top, observe, ticketId)  defined in test fails
        var trig = "<button onClick='" +
            "triggerBuilds(" +
            "\"" + srvId + "\", " +
            "\"" + suiteId + "\", " +
            "\"" + status.resolvedBranch + "\"," +
            " false," +
            " true," +
            "\"" + jiraOptional + "\")'";

        if (noNeedToTrigger) {
            trig += " class='disabledbtn'";
        }

        trig += ">Trigger build</button>";
        $("#triggerBuildFor" + prId).html(trig);
    }

    if (hasJiraIssue && isDefinedAndFilled(status.resolvedBranch)) {
        // triggerBuilds(serverId, suiteIdList, branchName, top, observe, ticketId)  defined in test fails
        var trigAndObs = "<button onClick='" +
            "triggerBuilds(" +
            "\"" + srvId + "\", " +
            "\"" + suiteId + "\", " +
            "\"" + status.resolvedBranch + "\"," +
            " false," +
            " false," +
            "\"" + jiraOptional + "\")'";

        if (noNeedToTrigger) {
            trigAndObs += " class='disabledbtn'";
        }

        trigAndObs += ">Trigger build and comment JIRA after finish</button>";

        $('#triggerAndObserveBuildFor' + prId).html(trigAndObs);
    }
}