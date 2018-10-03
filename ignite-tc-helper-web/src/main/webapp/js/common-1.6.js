function isDefinedAndFilled(val) {
    return typeof val !== 'undefined' && val != null
}

function findGetParameter(parameterName) {
    var result = null,
        tmp = [];
    location.search
        .substr(1)
        .split("&")
        .forEach(function(item) {
            tmp = item.split("=");
            if (tmp[0] === parameterName) result = decodeURIComponent(tmp[1]);
        });
    return result;
}

function componentToHex(c) {
    var hex = c.toString(16);
    return hex.length === 1 ? "0" + hex : hex;
}

function rgbToHex(r, g, b) {
    return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
}

//requires element on page: <div id="loadStatus"></div>
function showErrInLoadStatus(jqXHR, exception) {
    if (jqXHR.status === 0) {
        $("#loadStatus").html('Not connect.\n Verify Network.');
    } else if (jqXHR.status === 404) {
        $("#loadStatus").html('Requested page not found. [404]');
    } else if (jqXHR.status === 401) {
        $("#loadStatus").html('Unauthorized [401]');

        setTimeout(function() {
            window.location.href = "/login.html" + "?backref=" + encodeURIComponent(window.location.href);
        }, 1000);
    } else if (jqXHR.status === 403) {
        $("#loadStatus").html('Forbidden [403]');
    } else if( jqXHR.status === 418) {
        $("#loadStatus").html('Services are starting [418], I\'m a teapot');
    } else if (jqXHR.status === 424) {
        $("#loadStatus").html('Dependency problem: [424]: ' + jqXHR.responseText);
    } else if (jqXHR.status === 500) {
        $("#loadStatus").html('Internal Server Error [500].');
    } else if (exception === 'parsererror') {
        $("#loadStatus").html('Requested JSON parse failed.');
    } else if (exception === 'timeout') {
        $("#loadStatus").html('Time out error.');
    } else if (exception === 'abort') {
        $("#loadStatus").html('Ajax request aborted.');
    } else {
        $("#loadStatus").html('Uncaught Error.\n' + jqXHR.responseText);
    }
}


//requires element on page: <div id="version"></div>
function showVersionInfo(result) {
    var res = "<hr>";
    res += "<small><a href='https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot'>Apache Ignite Teamcity Bot</a>, V" + result.version + ", ";

    if (isDefinedAndFilled(result.srcWebUrl)) {
        res += "<a href='" + result.srcWebUrl + "'>source code (GitHub)</a>";
    }

    if (isDefinedAndFilled(result.apacheGitUrl)) {
        res += ", <a href='" + result.apacheGitUrl + "'>Apache Git</a>.";
    }

    res += "<br>Powered by <a href='https://ignite.apache.org/'>";
    res += "<img width='16px' height='16px' src='https://pbs.twimg.com/profile_images/568493154500747264/xTBxO73F.png'>";
    res += "Apache Ignite</a>";

    if (isDefinedAndFilled(result.ignVer)) {
        res += " V" + result.ignVer;
    }

    res += "<br>";
    res += "Managed by the <a href='https://ignite.apache.org/community/resources.html#people'>Apache Ignite Development Project.</a>";

    res += "<br>";

    res += "Copyright &#9400;, the Apache Software Foundation." +
        " Licensed under the <a href='http://www.apache.org/licenses/LICENSE-2.0'>Apache License, Version 2.0</a></small>";

    $("#version").html(res);
}

$(document).ready(function () {
    setupTokenManual();
    setupMenu();
});

var g_menuSet = false;

function setupMenu() {
    if (g_menuSet)
        return;

    g_menuSet = true;

    $.ajax({
        url: "rest/user/currentUserName",
        success: showMenu,
        error: function () {
            //not logged in

            showMenu({});
        }
    });
}

function showMenu(menuData) {
    var userName = menuData.result;
    var logoImage="";

    var res = "";
    if (!isDefinedAndFilled(userName)) {
        res += "<div class=\"navbar\">";
        res += logoImage;
        res += "<div class='topnav-right'>";
        res += "<a href='/login.html'>Login</a>";
        res += "</div>";
        res += "</div>";
    } else {
        res += "<div class=\"navbar\">";
        res += "<a href=\"/\">Home</a>";
        res += "<a href=\"/compare.html\">Compare builds</a>";
        res += "<a href=\"/services.html\">PR/Branch check</a>";
        res += "<a href=\"/comparison.html\">Master Trends</a>";


        res += "<div class='topnav-right'>";

        if(isDefinedAndFilled(menuData.authorizedState) && !menuData.authorizedState) {
            res += "<a onclick='authorizeServer()' href='javascript:void(0);'>Authorize Server</a>";
        }

        res += "<a href='/monitoring.html'>Server state</a>";

        res += "<a href='/user.html'>" + userName + "</a>";
        var logout = "/login.html" + "?exit=true&backref=" + encodeURIComponent(window.location.href);
        res += "<a href='" + logout + "'>Logout</a>";

        res += "</div>";
        res += "</div>";
    }

    $(document.body).prepend(res);
}


function authorizeServer() {
    $.ajax({
        type: "POST",
        url: "rest/user/authorize",
        success: resetMenu,
        error:   showErrInLoadStatus
    });
}

function resetMenu() {
    $(".navbar").html("");
    g_menuSet = false;
    setupMenu();
}

function setupTokenManual(result) {
    $.ajaxSetup({
        beforeSend: function (xhr) {
            try {
                var fullTok = window.sessionStorage.getItem("token");

                if (!isDefinedAndFilled(fullTok))  {
                    fullTok = window.localStorage.getItem("token");

                    if (!isDefinedAndFilled(fullTok))  {
                        fullTok = findGetParameter("auth_token");

                        if (isDefinedAndFilled(fullTok)) {
                            //don't persist provided token
                            window.sessionStorage.setItem("token", fullTok);
                        }
                    }
                }

                if (isDefinedAndFilled(fullTok)) {
                    xhr.setRequestHeader("Authorization", "Token " + fullTok);
                }
            } catch (e) {
            }
        }
    });
}

function tcHelperLogout() {
    try {
        var fullTok = window.sessionStorage.getItem("token");

        if (isDefinedAndFilled(fullTok))
            window.sessionStorage.removeItem("token");

        fullTok = window.localStorage.getItem("token");

        if (isDefinedAndFilled(fullTok))
            window.localStorage.removeItem("token");

    } catch (e) {
    }
}

/**
 * Change autocomplete filter to show results only when they starts from written text.
 */
$.ui.autocomplete.filter = function (array, term) {
    var matcher = new RegExp("^" + $.ui.autocomplete.escapeRegex(term), "i");

    return $.grep(array, function (value) {
        return matcher.test(value.label || value.value || value);
    });
};

var callbackRegistry = {};

/**
 * Send request to another site.
 *
 * @param url URL.
 * @param onSuccess Function for success response.
 * @param onError Function for fail response.
 */
function scriptRequest(url, onSuccess, onError) {
    var scriptOk = false;
    var callbackName = 'cb' + String(Math.random()).slice(-6);

    url += ~url.indexOf('?') ? '&' : '?';
    url += 'callback=callbackRegistry.' + callbackName;

    callbackRegistry[callbackName] = function(data) {
        scriptOk = true;

        delete callbackRegistry[callbackName];

        onSuccess(data);
    };

    function checkCallback() {
        if (scriptOk)
            return;

        delete callbackRegistry[callbackName];

        console.error("Request to \"" + url + "\" was failed.")
    }

    var script = document.createElement('script');

    script.onload = script.onerror = checkCallback;
    script.src = url;

    document.body.appendChild(script);
}

/** Key-value map. Key - server id. Value - url to git api. */
var gitUrls = new Map();

/** Branches for TeamCity. */
var branchesForTc = {};

/**
 * Fill git URLs and send requests to them.
 *
 * @param srvIds - Set of server IDs.
 */
function setupAutocompleteList(srvIds) {
    for (let srvId of srvIds)
        gitUrls.set(srvId, "");

    startFillAutocompleteListsProcess();
}

/**
 * Retrieves recent PR numbers and fills autocomplete lists for the fields branchForTc.
 */
function startFillAutocompleteListsProcess() {
    _receiveIntegrationUrls();
}

/**
 * Receive api links for the servers.
 *
 * @private
 */
function _receiveIntegrationUrls() {
    var url = "rest/build/integrationUrls?serverIds=";

    for (let key of gitUrls.keys())
        url += key + ",";

    $.ajax({
        url: url,
        success: function (result) {
            _fillGitUrls(result);
            _sendRequestsToFillAutocompleteLists();
        }
    });
}

/**
 * Fill git api URLs.
 *
 * @param result Array of ServerIntegrationLinks.
 *
 * @private
 */
function _fillGitUrls(result) {
    for (let links of result)
        gitUrls.set(links.srvId, links.gitApiUrl);
}

/**
 * Send requests to the git to get pull requests for the branch autocomplete lists.
 *
 * @private
 */
function _sendRequestsToFillAutocompleteLists() {
    for (var entry of gitUrls.entries())
        scriptRequest(entry[1] + "pulls?sort=updated&direction=desc", _fillBranchAutocompleteList);
}

/**
 * Takes all "branchForTc<server>" and add autocomplete list to them.
 *
 * @param result Response from git.
 */
function _fillBranchAutocompleteList(result) {
    if (!result.data || !result.data[0])
        return;

    for (var entry of gitUrls.entries()) {
        if (!result.data[0].url.startsWith(entry[1]))
            continue;

        branchesForTc[entry[0]] = [{label:"master", value:"refs/heads/master"}];

        for (let pr of result.data)
            branchesForTc[entry[0]].push({label: pr.number, value: "pull/" + pr.number + "/head"});

        $(".branchForTc" + entry[0]).autocomplete({source: branchesForTc[entry[0]]});
    }
}

/**
 * Fills autocomplete lists for the branchForTc fields, if lists are available.
 */
function tryToFillAutocompleteLists() {
    for (var entry of gitUrls.entries()) {
        var fields = $(".branchForTc" + entry[0]);

        for (let field of fields) {
            if (branchesForTc[entry[0]] && branchesForTc[entry[0]].length > 1 &&
                field.autocomplete("option", "source").length < 2)
                field.autocomplete({source: branchesForTc[entry[0]]});
        }
    }
}
