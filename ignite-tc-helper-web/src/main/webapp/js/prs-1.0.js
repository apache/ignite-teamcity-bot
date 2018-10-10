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
    // `rowData` is the original data object for the row

    let prId = row.prNumber;
    var res = "";
    res += "<div class='formgroup'>";
    res += "<table cellpadding='5' cellspacing='0' border='0' style='padding-left:50px;'>\n";

    //icon of stage
    res += "<tr>\n" +
        "                <th><span class='visaStage' id='visaStage_1_" + prId + "'></span></th>\n" +
        "                <th><span class='visaStage' id='visaStage_2_" + prId + "'></span></th>\n" +
        "                <th><span class='visaStage' id='visaStage_3_" + prId + "'></span></th>\n" +
        //  "                <th><span class='visaStage' id='visaStage_4_" + prId + "'></span></th>\n" +
        //todo validityCheck;"                <th><span class='visaStage' id='visaStage_5_" + prId + "'></span></th>\n" +
        "            </tr>\n";

    //caption of stage
    res += "<tr>\n" +
        "                <td>PR with issue name</td>\n" +
        "                <td>Build is triggered</td>\n" +
        "                <td>Results ready</td>\n" +
        // "                <td>JIRA comment</td>\n" +
        //todo  "                <td>Validity check</td>\n" +
        "            </tr>\n";

    //action for stage
    res += "        <tr>\n" +
        "            <td>Edit PR: " + "<a href='" + row.prHtmlUrl + "'>#" + row.prNumber + "</a>" + "</td>\n" +
        "               <td id='triggerBuildFor" + prId + "'>Loading builds...</td>\n" +
        "               <td id='showResultFor" + prId + "'>Loading builds...</td>\n" +
        "        </tr>" +
        "    </table>";

    res += "</div>";


    $.ajax({
        url: "rest/visa/contributionStatus?serverId=" + srvId +
            "&suiteId=" + suiteId +
            "&prId=" + prId,
        success:
            function (result) {
                let finishedBranch = result.branchWithFinishedRunAll;
                let tdForPr = $('#showResultFor' + prId);
                let buildIsCompleted = isDefinedAndFilled(finishedBranch);
                if (buildIsCompleted) {
                    tdForPr.html("<a id='link_" + prId + "' href='" + prShowHref(srvId, suiteId, finishedBranch) +  "'>" +
                        "<button id='show_" + prId + "'>Show " + finishedBranch + " report</button></a>");
                } else {
                    tdForPr.html("No builds, please trigger " + suiteId);
                }

                let hasQueued = result.queuedBuilds > 0 || result.runningBuilds > 0;

                let jiraIssue = isDefinedAndFilled(row.jiraIssueId);
                showStageResult(1, prId, jiraIssue, !jiraIssue);
                let noNeedToTrigger = hasQueued || buildIsCompleted;
                showStageResult(2, prId, noNeedToTrigger, false);
                showStageResult(3, prId, buildIsCompleted, false);

                if(isDefinedAndFilled(result.resolvedBranch)) {
                    var trig ="";
                    trig+= "<button onClick='triggerBuilds(\"" + srvId + "\", \"" + suiteId + "\", \"" +
                        result.resolvedBranch + "\", false, false)'" ;

                    if(noNeedToTrigger) {
                        trig+=" class='disabledbtn'";
                    }
                    trig+=">Trigger build</button>";
                    $('#triggerBuildFor' + prId).html(trig);
                }
            }
    });
    return res;
}