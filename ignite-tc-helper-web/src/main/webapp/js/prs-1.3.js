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
const prReportDefaultSuites = new Map();
const contributionsByServer = new Map();
const myGithubLoginsByServer = new Map();
const ONLY_MY_PRS_STORAGE_PREFIX = "tcbot.prs.onlyMyPrs.";

function onlyMyPrsStorageKey(srvId) {
    return ONLY_MY_PRS_STORAGE_PREFIX + srvId;
}

function loadOnlyMyPrsPreference(srvId) {
    try {
        return window.localStorage.getItem(onlyMyPrsStorageKey(srvId)) === "true";
    }
    catch (e) {
        return false;
    }
}

function saveOnlyMyPrsPreference(srvId, value) {
    try {
        window.localStorage.setItem(onlyMyPrsStorageKey(srvId), value ? "true" : "false");
    }
    catch (e) {
        // Ignore unavailable storage; page filtering still works for this session.
    }
}

function drawTable(srvId, element) {
    let tableId = "serverContributions-" + srvId;
    let onlyMyPrsChecked = loadOnlyMyPrsPreference(srvId);

    element.append("<div id='contributionsActions-" + srvId + "' align='right' " +
        "style='margin-right:50px; display:flex; justify-content:flex-end; gap:4px; align-items:center'>" +
        "<label id='onlyMyPrsBlock-" + srvId + "' style='display:none' " +
        "title='Show only PRs whose cached GitHub author matches your bot profile by email or configured GitHub IDs'>" +
        "<input id='onlyMyPrs-" + srvId + "' type='checkbox' " +
        (onlyMyPrsChecked ? "checked" : "") + "> Only my PRs</label>" +
        "<button id='refreshContributions-" + srvId + "' type='button' title='Load current PR data from GitHub now'>" +
        "Refresh now</button>" +
        "<span id='expandAllButton-" + srvId + "'></span>" +
        "</div><br>" +
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

    $("#refreshContributions-" + srvId).on("click", function () {
        refreshContributionsNow(srvId);
    });

    $("#onlyMyPrs-" + srvId).on("change", function () {
        saveOnlyMyPrsPreference(srvId, $(this).prop("checked"));
        renderContributionsTable(srvId, "");
    });
}

function rememberDefaultSuitesForPrReport(result) {
    if (!isDefinedAndFilled(result))
        return;

    for (let i = 0; i < result.length; i++) {
        let chain = result[i];

        if (isDefinedAndFilled(chain.serverId) && isDefinedAndFilled(chain.suiteId)
            && !prReportDefaultSuites.has(chain.serverId))
            prReportDefaultSuites.set(chain.serverId, chain.suiteId);
    }

    refreshEarlyPrReportLinks();
}

function defaultSuiteForPrReport(srvId, suiteId) {
    if (isDefinedAndFilled(suiteId))
        return suiteId;

    let querySuite = findGetParameter("suiteId");

    if (isDefinedAndFilled(querySuite))
        return querySuite;

    return prReportDefaultSuites.get(srvId);
}

function branchForPrReport(row) {
    if (isDefinedAndFilled(row.tcBranchName))
        return row.tcBranchName;

    if (row.prNumber > 0)
        return branchForTc(String(row.prNumber));

    return "";
}

function prReportLinkHtml(srvId, suiteId, branchName, label) {
    return "<a href='" + escapeHtml(prShowHref(srvId, suiteId, branchName)) + "'>" +
        "<button type='button' class='disabledbtn' " +
        "title='Report status is still loading; the build may still be missing in the bot cache'>" +
        escapeHtml(label) + "</button></a>";
}

function earlyPrReportHtml(row, srvId, suiteId) {
    let branchName = branchForPrReport(row);
    let selectedSuite = defaultSuiteForPrReport(srvId, suiteId);
    let attrs = " class='early-pr-report'" +
        " data-srv='" + escapeHtml(srvId) + "'" +
        " data-branch='" + escapeHtml(branchName) + "'";

    if (isDefinedAndFilled(selectedSuite)) {
        attrs += " data-suite='" + escapeHtml(selectedSuite) + "'";

        return "<span" + attrs + ">" +
            prReportLinkHtml(srvId, selectedSuite, branchName, "Open PR report") +
            "</span>";
    }

    return "<span" + attrs + ">Loading report link...</span>";
}

function refreshEarlyPrReportLinks() {
    $(".early-pr-report").each(function () {
        let item = $(this);
        let srvId = item.attr("data-srv");
        let branchName = item.attr("data-branch");
        let suiteId = item.attr("data-suite");

        if (!isDefinedAndFilled(suiteId))
            suiteId = defaultSuiteForPrReport(srvId, null);

        if (isDefinedAndFilled(suiteId) && isDefinedAndFilled(branchName)) {
            item.attr("data-suite", suiteId);
            item.html(prReportLinkHtml(srvId, suiteId, branchName, "Open PR report"));
        }
    });
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
                loadGithubResolutionForContributions(srvId);
                fillBranchAutocompleteList(result, srvId);
                setAutocompleteFilter();
            }
    });
}

function refreshContributionsNow(srvId) {
    let button = $("#refreshContributions-" + srvId);
    let dialog = $("#refreshContributionsDialog");

    if (dialog.length > 0) {
        if (dialog.data("ui-dialog"))
            dialog.dialog("destroy");

        dialog.remove();
    }

    $("body").append("<div id='refreshContributionsDialog' title='Refresh pull requests'>" +
        actionStatusHtml("Preparing PR refresh.") +
        actionStagesHtml(false, "refreshContributionsStages") +
        actionErrorHtml() +
        "</div>");
    dialog = $("#refreshContributionsDialog");

    let stages = $("#refreshContributionsStages");
    let processId = createBotProcessId("refreshContributions");
    let stopProcessPolling;
    let xhr;

    function appendStage(text) {
        appendActionStage(dialog, text);
        stages.scrollTop(stages.prop("scrollHeight"));
    }

    function finishProgress(buttons) {
        if (stopProcessPolling)
            stopProcessPolling();

        dialog.dialog("option", "buttons", buttons);
        button.prop("disabled", false);
    }

    button.prop("disabled", true);
    stages.empty();
    dialog.find(".action-error").hide().empty();
    setActionStatus(dialog, "Refreshing " + srvId + " contributions from GitHub...");

    openCenteredDialog(dialog, actionDialogOptions("Refresh pull requests", {}));

    appendStage("Sending PR refresh request to the bot REST API.");

    stopProcessPolling = startBotProcessPolling(processId, function (processStatus) {
        appendStage(botProcessStatusText(processStatus));
    });

    xhr = $.ajax({
        url: "rest/visa/contributions/refresh?serverId=" + encodeURIComponent(srvId) +
            "&processId=" + encodeURIComponent(processId),
        success: function (result) {
            showContributionsTable(result, srvId, "");
            loadGithubResolutionForContributions(srvId);
            fillBranchAutocompleteList(result, srvId);
            setAutocompleteFilter();

            setActionStatus(dialog, "Done. Loaded " + result.length + " contributions.");
            appendStage("Table was updated with fresh GitHub data.");
            finishProgress({
                "Ok": function () {
                    $(this).dialog("close");
                }
            });
        },
        error: function (jqXHR, textStatus, errorThrown) {
            if (textStatus === "abort")
                return;

            setActionStatus(dialog, "Refresh failed.");
            appendStage("Error: " + (errorThrown || jqXHR.statusText || "unknown error"));
            dialog.find(".action-error").text(jqXHR.responseText || errorThrown || "Unknown request error.").show();
            finishProgress({
                "Ok": function () {
                    $(this).dialog("close");
                }
            });
        }
    });

    dialog.dialog("option", "close", function () {
        if (xhr != null && xhr.readyState !== 4)
            xhr.abort();

        if (stopProcessPolling)
            stopProcessPolling();

        button.prop("disabled", false);
    });
}

function loadGithubResolutionForContributions(srvId) {
    $.ajax({
        url: "rest/user/githubResolution?serverId=" + encodeURIComponent(srvId),
        success: function (result) {
            updateGithubResolution(srvId, result);
            renderContributionsTable(srvId, "");
        },
        error: function (jqXHR) {
            myGithubLoginsByServer.set(srvId, new Set());
            updateOnlyMyPrsControl(srvId, "GitHub profile resolution failed: " +
                (jqXHR.statusText || "unknown error"));
            renderContributionsTable(srvId, "");
        }
    });
}

function hasResolvedGithubLogins(srvId) {
    let logins = myGithubLoginsByServer.get(srvId);

    return isDefinedAndFilled(logins) && logins.size > 0;
}

function resolvedGithubLoginsText(srvId) {
    let logins = myGithubLoginsByServer.get(srvId);

    if (!isDefinedAndFilled(logins) || logins.size === 0)
        return "";

    return Array.from(logins).join(", ");
}

function isMyGithubLogin(srvId, login) {
    let logins = myGithubLoginsByServer.get(srvId);

    return isDefinedAndFilled(login) && isDefinedAndFilled(logins) &&
        logins.has(String(login).toLowerCase());
}

function updateGithubResolution(srvId, result) {
    let logins = new Set();

    if (isDefinedAndFilled(result) && isDefinedAndFilled(result.allLogins)) {
        for (let i = 0; i < result.allLogins.length; i++)
            logins.add(String(result.allLogins[i]).toLowerCase());
    }

    myGithubLoginsByServer.set(srvId, logins);
    updateOnlyMyPrsControl(srvId);
}

function claimGithubAuthor(srvId, githubId) {
    $.ajax({
        method: "POST",
        url: "rest/user/claimGithubId?serverId=" + encodeURIComponent(srvId),
        data: {
            githubId: githubId
        },
        success: function (result) {
            updateGithubResolution(srvId, result);
            renderContributionsTable(srvId, "");
        },
        error: showErrInLoadStatus
    });
}

function confirmClaimGithubAuthor(srvId, githubId) {
    var dialog = ensureActionDialog("claimGithubAuthorDialog", "Confirm GitHub profile match");

    dialog.html(
        "<div>You are about to add <b>" + escapeHtml(githubId) + "</b> to your GitHub IDs.</div>" +
        "<div style='margin-top:10px'>After this, TCBot will treat PRs authored by this GitHub account as yours " +
        "for PR filters and author matching.</div>" +
        "<div style='margin-top:10px; color:#666'>No change will be saved if you close this dialog.</div>"
    );

    openActionDialog(dialog, "Confirm GitHub profile match", {
        "Confirm": function () {
            $(this).dialog("close");
            claimGithubAuthor(srvId, githubId);
        },
        "Cancel": function () {
            $(this).dialog("close");
        }
    });
}

function claimGithubAuthorHtml(srvId, row) {
    if (!isDefinedAndFilled(row) || !isDefinedAndFilled(row.prAuthor) || !myGithubLoginsByServer.has(srvId) ||
        isMyGithubLogin(srvId, row.prAuthor))
        return "";

    return "<br><a href='javascript:void(0);' title='Confirm adding " + escapeHtml(row.prAuthor) +
        " to your GitHub IDs' onclick='" + jsCallAttr("confirmClaimGithubAuthor", [srvId, row.prAuthor]) +
        "'>it's me</a>";
}

function myPrsCountInTable(srvId) {
    let logins = myGithubLoginsByServer.get(srvId);
    let rows = contributionsByServer.get(srvId);

    if (!hasResolvedGithubLogins(srvId) || !isDefinedAndFilled(rows))
        return 0;

    let cnt = 0;

    for (let i = 0; i < rows.length; i++) {
        if (isMyGithubLogin(srvId, rows[i].prAuthor))
            cnt++;
    }

    return cnt;
}

function updateOnlyMyPrsControl(srvId, err) {
    let block = $("#onlyMyPrsBlock-" + srvId);
    let checkbox = $("#onlyMyPrs-" + srvId);
    let title = "Show only PRs whose cached GitHub author matches your bot profile by email or configured GitHub IDs";

    if (isDefinedAndFilled(err)) {
        checkbox.prop("checked", false);
        block.attr("title", err);
        block.hide();
    }
    else if (hasResolvedGithubLogins(srvId)) {
        let cnt = myPrsCountInTable(srvId);

        checkbox.prop("checked", loadOnlyMyPrsPreference(srvId));
        block.attr("title", title + ". Resolved GitHub IDs: " + resolvedGithubLoginsText(srvId) +
            ". Matching PRs in the current table: " + cnt + ".");
        block.show();
    }
    else {
        checkbox.prop("checked", false);
        block.attr("title", title + ". Configure GitHub IDs in your user profile to enable this filter.");
        block.hide();
    }
}

function currentContributionRows(srvId) {
    let rows = contributionsByServer.get(srvId) || [];
    let logins = myGithubLoginsByServer.get(srvId);

    if (!$("#onlyMyPrs-" + srvId).prop("checked") || !isDefinedAndFilled(logins) || logins.size === 0)
        return rows;

    return rows.filter(function (row) {
        return isMyGithubLogin(srvId, row.prAuthor);
    });
}

function normalizeDateNum(num) {
    return num < 10 ? '0' + num : num;
}

function showContributionsTable(result, srvId, suiteId) {
    contributionsByServer.set(srvId, result || []);
    updateOnlyMyPrsControl(srvId);
    renderContributionsTable(srvId, suiteId);
}

function renderContributionsTable(srvId, suiteId) {
    let tableId = 'serverContributions-' + srvId;
    let tableForSrv = $('#' + tableId);
    let result = currentContributionRows(srvId);

    tableForSrv.dataTable().fnDestroy();

    if (isDefinedAndFilled(result) && result.length > 0)
        $("#expandAllButton-" + srvId).html("<button class='more green' id='expandAll-" + srvId +
            "' type='button'>Expand all</button>");

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
                    if (!isDefinedAndFilled(data))
                        data = "";

                    if (type === 'display' && isDefinedAndFilled(row.prAuthorAvatarUrl) && row.prAuthorAvatarUrl.length >0) {
                        data = "<img src='" + escapeHtml(row.prAuthorAvatarUrl) +
                            "' loading='lazy' onerror='this.style.display=\"none\"' " +
                            "style='width:20px; height:20px; border-radius:50%; object-fit:cover; " +
                            "vertical-align:middle; margin-right:6px'> " + escapeHtml(data);
                    }
                    else if (type === 'display')
                        data = escapeHtml(data);

                    if (type === 'display' && isDefinedAndFilled(row.prAuthorUrl))
                        data = "<a href='" + escapeHtml(row.prAuthorUrl) + "'>" + data + "</a>";

                    if (type === 'display')
                        data += claimGithubAuthorHtml(srvId, row);

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

    $('#expandAll-' + srvId).on('click', function () {
        $('#' + tableId + ' .details-control').click();
    });

    // Add event listener for opening and closing details, enable to only btn   'td.details-control'
    $('#' + tableId + ' tbody').off('click', 'td.details-control').on('click', 'td.details-control', function () {
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
    stageOneStatus.html("...");
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
function formatContributionDetails(row, srvId, suiteId) {
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
        "                <td>Comments</td>\n" +
        "            </tr>\n";

    //icon of stage
    res += "<tr>\n" +
        "                <th title='PR should have valid naming starting with issue name'><span class='visaStage' id='visaStage_1_" + prId + "'></span></th>\n" +
        "                <th title='Suite should be triggered'><span class='visaStage' id='visaStage_2_" + prId + "'></span></th>\n" +
        "                <th><span class='visaStage' id='visaStage_3_" + prId + "'></span></th>\n" +
         "               <th><span class='visaStage' id='visaStage_4_" + prId + "'></span></th>\n" +
        "            </tr>\n";

    //action for stage
    res += "        <tr>\n" +
        "            <td></td>\n" +
        "            <td id='triggerBuildFor" + prId + "'>Loading builds...</td>\n" +
        "            <td id='showResultFor" + prId + "'>" + earlyPrReportHtml(row, srvId, suiteId) + "</td>\n" +
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

                let isDefault = [],
                    isCompleted = [],
                    isIncompleted = [],
                    suites = new Map();

                //See also org.apache.ignite.ci.tcbot.visa.ContributionCheckStatus
                for (let status of result) {
                    suites.set(status.suiteId, status);

                    if (status.defaultBuildType === true)
                        isDefault.push(status);
                    else if (isDefinedAndFilled(status.branchWithFinishedSuite))
                        isCompleted.push(status);
                    else
                        isIncompleted.push(status);
                }

                for (let status of isDefault)
                    selectHtml += "<option value='true' style='font-weight: bold; color: darkblue'>" + status.suiteId + "</option>";

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
    let commentCell = $('#commentJiraFor' + prId);

    if (!isDefinedAndFilled(status)) {
        console.log("Status for " + prId + " is undefined. Wait for the Bot to load the suite list.");

        return;
    }

    commentCell.empty();

    let buildIsCompleted = isDefinedAndFilled(status.branchWithFinishedSuite);
    let hasJiraIssue = isDefinedAndFilled(row.jiraIssueId);
    var jiraOptional = hasJiraIssue ? row.jiraIssueId : "";
    let actionUiLinks = {
        ticketLink: isDefinedAndFilled(row.jiraIssueUrl) ? row.jiraIssueUrl : "",
        prLink: isDefinedAndFilled(row.prHtmlUrl) ? row.prHtmlUrl : ""
    };
    let githubCleanOnlyUiLinks = {
        ticketLink: isDefinedAndFilled(row.jiraIssueUrl) ? row.jiraIssueUrl : "",
        prLink: isDefinedAndFilled(row.prHtmlUrl) ? row.prHtmlUrl : "",
        commentPolicyHint: "By default GitHub will be commented only if no blockers are found. " +
            "If you need a GitHub comment for any result, switch the option to Comment always."
    };
    let hasQueued = status.queuedBuilds > 0 || status.runningBuilds > 0;
    let queuedStatus = "Has queued builds: " + status.queuedBuilds  + " queued " + " " + status.runningBuilds  + " running";

    var linksToRunningBuilds = "";
    var tcIconUrl = "";
    for (let i = 0; i < status.webLinksQueuedSuites.length; i++) {
        const l = status.webLinksQueuedSuites[i];
        if (!isDefinedAndFilled(tcIconUrl))
            tcIconUrl = l;
        linksToRunningBuilds += "<a href='" + escapeHtml(l) + "'>View queued at TC</a> "
    }
    $('#viewQueuedBuildsFor' + prId).html(linksToRunningBuilds);

    if (buildIsCompleted) {
        let finishedBranch = status.branchWithFinishedSuite;

        let reportLink = "<a id='showReportlink_" + prId + "' href='" + prShowHref(srvId, suiteIdSelected, finishedBranch) + "'>" +
            "<button id='show_" + prId + "'>Open PR report</button>" +
            "</a>";
        if(isDefinedAndFilled(status.finishedSuiteCommit)) {
            reportLink += "<br>(" + status.finishedSuiteCommit + ")";
        }


        tdForPr.html(reportLink);

        let commentBtns = "<span style='display:inline-flex; gap:4px; align-items:center; white-space:nowrap'>";

        if (hasJiraIssue) {
            commentBtns += "<button onclick='" + jsCallAttr("commentJira", [
                srvId, finishedBranch, suiteIdSelected, row.jiraIssueId,
                "", "JIRA", row.prNumber, false, actionUiLinks
            ]) + "'";

            if (hasQueued) {
                commentBtns += " class='disabledbtn' title='" + escapeHtml(queuedStatus) + "'";
            }
            commentBtns += ">" + buttonLabel("Comment JIRA", row.jiraIssueUrl, null) + "</button> ";
        }

        if (row.prNumber > 0) {
            commentBtns += "<button onclick='" + jsCallAttr("commentJira", [
                srvId, finishedBranch, suiteIdSelected, jiraOptional,
                "", "GITHUB", row.prNumber, false, actionUiLinks
            ]) + "'";

            if (hasQueued)
                commentBtns += " class='disabledbtn' title='" + escapeHtml(queuedStatus) + "'";

            commentBtns += ">" + buttonLabel("Comment GitHub", row.prHtmlUrl, null) + "</button>";
        }

        commentBtns += "</span>";

        commentCell.html(commentBtns);
    } else {
        let noBuildsHtml = "No builds for " + escapeHtml(suiteIdSelected);

        if (!isDefinedAndFilled(status.resolvedBranch))
            noBuildsHtml += ", please trigger it when branch is resolved";

        tdForPr.html(noBuildsHtml);
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
        showCommentObservationStatus(commentCell, status.observationsStatus);
    }

    function prepareStatusOfTrigger() {
        var res  = "";
        if (hasQueued || buildIsCompleted) {
            res += " class='disabledbtn'";
            if (hasQueued)
                res += " title='" + escapeHtml(queuedStatus) + "'";
            else
                res += " title='Results are ready. It is still possible to trigger Build'";
        }
        return res;
    }

    if (isDefinedAndFilled(status.resolvedBranch)) {
        // triggerBuilds(serverId, suiteIdList, branchName, top, observe, ticketId)  defined in test fails
        let triggerBuildsCall = jsCall("triggerBuilds", [
            srvId, null, suiteIdSelected, status.resolvedBranch,
            false, false, jiraOptional, row.prNumber, null, false, "", false, actionUiLinks
        ]);
        var res = "<button onClick='" + jsEventAttr([triggerBuildsCall, jsCall("repaintLater", [srvId])]) + "'";
        res += prepareStatusOfTrigger();

        res += ">" + buttonLabel("Trigger build", tcIconUrl, null) + "</button>";

        if (hasJiraIssue) {
            let trigObserveCall = jsCall("triggerBuilds", [
                srvId, null, suiteIdSelected, status.resolvedBranch,
                false, true, jiraOptional, row.prNumber, null, false, "JIRA", false, actionUiLinks
            ]);

            res += " <button onClick='" + jsEventAttr([trigObserveCall, jsCall("repaintLater", [srvId])]) + "'";
            res += prepareStatusOfTrigger();
            res += ">" + buttonLabel("Trigger + JIRA", tcIconUrl, row.jiraIssueUrl) + "</button>";
        }

        if (row.prNumber > 0) {
            let trigGithubCall = jsCall("triggerBuilds", [
                srvId, null, suiteIdSelected, status.resolvedBranch,
                false, true, jiraOptional, row.prNumber, null, false, "GITHUB", true, githubCleanOnlyUiLinks
            ]);

            res += " <button onClick='" + jsEventAttr([trigGithubCall, jsCall("repaintLater", [srvId])]) + "'";
            res += prepareStatusOfTrigger();
            res += ">" + buttonLabel("Trigger + GitHub", tcIconUrl, row.prHtmlUrl) + "</button>";
        }

        $("#triggerBuildFor" + prId).html(res);
        $('#triggerAndObserveBuildFor' + prId).empty();
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

function showCommentObservationStatus(commentCell, statusText) {
    if (!isDefinedAndFilled(statusText))
        return;

    commentCell.append(
        "<div style='margin-top:4px; color:#666; font-size:12px; white-space:normal'>" +
        escapeHtml(statusText) +
        "</div>"
    );
}
