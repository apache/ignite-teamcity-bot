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
                "data": "prNumber",
                title: "Existing RunAll",
                "render": function (data, type, row, meta) {
                    let prId = data;
                    if (type === 'display') {
                        data = "<a id='link_" + prId + "' href='" +
                            prShowHref(srvId, suiteId, "pull%2F" + prId + "%2Fhead") +
                            "'>" +
                            "<button id='show_" + prId + "'>Open /" + data + "/head</button></a>";

                        // todo slow service
                        /*
                        $.ajax({
                            url: "rest/visa/findBranchForPr?serverId=" + srvId +
                                "&suiteId=" + suiteId +
                                "&prId=" + prId,
                            success:
                                function (result) {
                                    console.log("Contribution " + prId + " bransh: " + result + " ");
                                    if(isDefinedAndFilled(result.result)) {
                                        $('#link_' + prId).attr('href', prShowHref(srvId, suiteId, result.result));

                                    } else {
                                        $('#show_' + prId).attr('class', 'disabledbtn');
                                    }
                                }
                        });
                        */

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

function showButtonForPr(srvId, suiteId, prId, branchName) {
    var showRunRes = "<a id='link_" + prId + "' href='" +
        prShowHref(srvId, suiteId, branchName) +
        "'>" +
        "<button id='show_" + prId + "'>Show " + branchName + " branch report</button></a>";

    return showRunRes;
}

/* Formatting function for row details - modify as you need */
function formatContributionDetails(rowData, srvId, suiteId) {
    // `rowData` is the original data object for the row

    let prId = rowData.prNumber;
    let res = "<table cellpadding='5' cellspacing='0' border='0' style='padding-left:50px;'>\n" +
        "        <tr>\n" +
        "            <td>PR number:</td>\n" +
        "            <td>" + rowData.prNumber + "</td>\n" +
        "        </tr>" +
        "        <tr>\n" +
        "            <td>Show Run All Results:</td>\n" +
        "            <td id='branchFor_" + prId + "'>Loading builds...</td>\n" +
        "        </tr>\n" +
        "    </table>";
    $.ajax({
        url: "rest/visa/findBranchForPr?serverId=" + srvId +
            "&suiteId=" + suiteId +
            "&prId=" + prId,
        success:
            function (result) {
                // console.log("Contribution " + prId + " bransh: " + result + " ");
                let branchName = result.result;
                let tdForPr = $('#branchFor_' + prId);
                if (isDefinedAndFilled(branchName)) {
                    tdForPr.html(showButtonForPr(srvId, suiteId, prId, branchName));
                } else {
                    tdForPr.html("No builds, please trigger " + suiteId);
                }
            }
    });
    return res;
}