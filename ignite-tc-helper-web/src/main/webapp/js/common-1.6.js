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
var more = "<button class='more white short'><i class='fas fa-caret-down'></i></button>";
var less = "<button class='more white short'><i class='fas fa-caret-up'></i></button>";

function isDefinedAndFilled(val) {
    return typeof val !== 'undefined' && val != null
}

/**
 * Function return URL parameter from webUrl (if is defined and filled) or from current location of the document.
 *
 * @returns {string | null} Search parameter or null.
 * @param {String} parameterName - Search parameter name.
 * @param {String | null} webUrl - URL.
 */
function findGetParameter(parameterName, webUrl) {
    if (isDefinedAndFilled(webUrl)) {
        let url = new URL(webUrl);

        return url.searchParams.get(parameterName);
    }

    let result = null,
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

    if (isDefinedAndFilled(result.gitHubMirror)) {
        res += "<a href='" + result.gitHubMirror + "'>source code (GitHub)</a>";
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

    if (isDefinedAndFilled(result.javaVer)) {
        res += ", Java Version: " + result.javaVer;
    }

    res += "<br>";
    res += "Managed by the <a href='https://ignite.apache.org/our-community.html'>Apache Ignite Development Project.</a>";

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
        url: "/rest/user/currentUserName",
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
        res += "<a href=\"/\" title='Home Page'><img src='/img/leaf-icon-png-7066.png' width='16px' height='16px'></a>";
        res += "<a href=\"/prs.html\" title='PR or branch check'>PR Check</a>";
        res += "<a href=\"/guard.html\" title='Monitoring: Current test failures in tracked Branches'>Test Status</a>";
        res += "<a href=\"/trends.html\" title='Monitoring: Test failures trends and graphs'>Master Trends</a>";
        res += "<a href=\"/longRunningTestsReport.html\" title='Monitoring: Long running tests report''>Test Durations</a>";
        res += "<a href=\"/buildtime.html\" title='Top suites time usage'>Suite Durations</a>";
        res += "<a href=\"/compare.html\" title='Compare builds tests test'>Compare builds</a>";
        res += "<a href=\"/issues.html\" title='Detected issues list'>Issues history</a>";
        res += "<a href=\"/visas.html\" title='Issued TC Bot Visa history'>Visas history</a>";
        res += "<a href=\"/mutes.html\" title='Muted tests list'>Muted tests</a>";
        res += "<a href=\"/mutedissues/index.html\" title='Muted issues list'>Muted issues</a>";
        res += "<a href=\"/board/index.html\" title='Board'>Board</a>";

        res += "<div class='topnav-right'>";

        if(isDefinedAndFilled(menuData.authorizedState) && !menuData.authorizedState) {
            res += "<a onclick='authorizeServer()' href='javascript:void(0);'>Authorize Server</a>";
        }

        res += "<a href='/monitoring.html'>Server state</a>";

        res += "<a id='userName' href='/user.html'>" + userName + "</a>";
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
        url: "/rest/user/authorize",
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
function setAutocompleteFilter() {
    $.ui.autocomplete.filter = function (array, term) {
        var matcher = new RegExp("^" + $.ui.autocomplete.escapeRegex(term), "i");

        return $.grep(array, function (value) {
            return matcher.test(value.label || value.value || value);
        });
    };
}

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
 * Fill autocomplete lists for the fields branchForTc.
 *
 * @param result List of ContributionToCheck.
 * @param srvId Server id.
 */
function fillBranchAutocompleteList(result, srvId) {
    if (!isDefinedAndFilled(result))
        return;

    if (!isDefinedAndFilled(gitUrls.get(srvId)))
        gitUrls.set(srvId, "");

    branchesForTc[srvId] = [{label:"master", value:"refs/heads/master"}];

    for (let pr of result) {
        branchesForTc[srvId].push({label: pr.prNumber + " " + pr.prTitle, value: "pull/" + pr.prNumber + "/head"});
        branchesForTc[srvId].push({label: "pull/" + pr.prNumber + "/head " + pr.prTitle,
            value: "pull/" + pr.prNumber + "/head"});
    }

    $(".branchForTc" + srvId).autocomplete({source: branchesForTc[srvId]});
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

/**
* Inits "More/Hide" UI element allowing to show/hide blocks of additional info.
*/
function initMoreInfo() {
    var header = $(".header");

    header.unbind("click");
    header.click(function() {
        $header = $(this);
        //getting the next element
        $content = $header.next();
        //open up the content needed, toggle the slide: slide up if visible, slide down if not.
        $content.slideToggle(500, function() {
            //execute this after slideToggle is done
            //change text of header based on visibility of content div
            $header.html(function() {
                //change text based on condition
                return $content.is(":visible") ? less : more;
            });
        });
    });
}
