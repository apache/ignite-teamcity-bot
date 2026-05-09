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

function isLoginUrl(url) {
    try {
        return new URL(url, window.location.origin).pathname === "/login.html";
    }
    catch (e) {
        return false;
    }
}

function escapeHtml(str) {
    return String(str == null ? "" : str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function escapeJsString(str) {
    return JSON.stringify(str == null ? "" : String(str));
}

function jsArg(value) {
    if (typeof value === "undefined" || value === null)
        return "null";

    if (typeof value === "number" || typeof value === "boolean")
        return String(value);

    if (typeof value === "object")
        return JSON.stringify(value);

    return escapeJsString(value);
}

function jsCall(name, args) {
    return name + "(" + (args || []).map(jsArg).join(", ") + ")";
}

function jsCallAttr(name, args) {
    return escapeHtml(jsCall(name, args));
}

function jsEventAttr(calls) {
    return escapeHtml((calls || []).join("; ") + ((calls || []).length === 0 ? "" : ";"));
}

function createBotProcessId(kind) {
    return Date.now() * 1000 + Math.floor(Math.random() * 1000);
}

function botProcessStatusText(status) {
    return status && isDefinedAndFilled(status.status) && status.status !== ""
        ? status.status
        : "Waiting for the bot to publish status.";
}

function startBotProcessPolling(processId, onStatus, options) {
    if (!isDefinedAndFilled(processId))
        return function () {};

    var opts = options || {};
    var lastStatus = null;
    var stopped = false;
    var timer;

    function poll() {
        if (stopped)
            return;

        $.ajax({
            url: "rest/process/status",
            data: {id: processId},
            success: function (status) {
                if (stopped || !status)
                    return;

                if (!isDefinedAndFilled(status.kind) && opts.skipUnknown !== false)
                    return;

                if (status.status !== lastStatus) {
                    lastStatus = status.status;
                    onStatus(status);
                }

                if (status.running === false)
                    stop();
            }
        });
    }

    function stop() {
        stopped = true;

        if (timer)
            clearInterval(timer);
    }

    timer = setInterval(poll, opts.intervalMs || 1500);
    poll();

    return stop;
}

function currentBackref() {
    if (isLoginUrl(window.location.href))
        return "/";

    return window.location.href;
}

//requires element on page: <div id="loadStatus"></div>
function showErrInLoadStatus(jqXHR, exception) {
    if (jqXHR.status === 0) {
        $("#loadStatus").html('Not connect.\n Verify Network.');
    } else if (jqXHR.status === 404) {
        $("#loadStatus").html('Requested page not found. [404]');
    } else if (jqXHR.status === 401) {
        var authMsg = isDefinedAndFilled(jqXHR.responseText)
            ? jqXHR.responseText
            : 'Unauthorized [401]';

        $("#loadStatus").text(authMsg);

        if (window.location.pathname === "/login.html")
            return;

        setTimeout(function() {
            window.location.href = "/login.html?authError=" + encodeURIComponent(authMsg)
                + "&backref=" + encodeURIComponent(currentBackref());
        }, 1000);
    } else if (jqXHR.status === 403) {
        $("#loadStatus").html('Forbidden [403]');
    } else if( jqXHR.status === 418) {
        $("#loadStatus").html('Services are starting [418], I\'m a teapot');
    } else if (jqXHR.status === 424) {
        $("#loadStatus").html('Dependency problem: [424]: ' + jqXHR.responseText);
    } else if (jqXHR.status === 500) {
        var serverMsg = isDefinedAndFilled(jqXHR.responseText)
            ? jqXHR.responseText
            : 'Internal Server Error [500].';

        $("#loadStatus").text(serverMsg);
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

function openAiPrompt(url) {
    openTextCommandDialog({
        dialogId: "aiPromptDialog",
        statusId: "aiPromptStatus",
        logId: "aiPromptProgressLog",
        errorId: "aiPromptError",
        title: "Generating AI prompt",
        initialMode: true,
        processKind: "aiPrompt",
        requestUrl: function (waitForTc, processId) {
            return aiPromptUrlWithWaitForTc(url, waitForTc, processId);
        },
        timeoutMs: 70000,
        skip: {
            isVisible: function (waitForTc) {
                return waitForTc;
            },
            nextMode: false,
            buttonText: "Use current context now",
            runningText: "Using current context...",
            stepText: "Building prompt from current cached context."
        },
        statusText: function (waitForTc) {
            return waitForTc ? "Generating prompt..." : "Generating prompt from current context...";
        },
        progressMessage: aiPromptProgressMessage,
        openButtonText: "Open prompt",
        downloadButtonText: "Download .txt",
        downloadFileName: "ai-prompt.txt",
        readyStatusText: "AI prompt is ready.",
        readyStepText: "Prompt text is ready. Use Open prompt or Download .txt.",
        failureStatusText: "AI prompt request failed.",
        failureMessagePrefix: "AI prompt request failed: "
    });
}

function openTextCommandDialog(options) {
    let state = createTextCommandDialog(options);

    requestTextCommand(options, state, options.initialMode);
}

function requestTextCommand(options, state, mode, firstStep) {
    if (state.timer)
        clearInterval(state.timer);

    if (state.processPollStop)
        state.processPollStop();

    state.processId = options.processKind ? createBotProcessId(options.processKind) : options.processId;

    let skip = options.skip;
    let skipVisible = skip != null && (skip.isVisible == null || skip.isVisible(mode));

    state.skipBtn.toggle(skipVisible).prop("disabled", false)
        .text(skip == null ? "" : skip.buttonText);
    state.openBtn.hide();
    state.downloadBtn.hide();
    state.errorBlock.hide();

    state.skipBtn.off("click");

    if (skipVisible) {
        state.skipBtn.on("click", function () {
            if (state.xhr)
                state.xhr.abort();

            let nextMode = typeof skip.nextMode === "function" ? skip.nextMode(mode) : skip.nextMode;

            state.skipBtn.prop("disabled", true).text(skip.runningText);
            requestTextCommand(options, state, nextMode, skip.stepText);
        });
    }

    startTextCommandProgress(options, state, mode, firstStep);

    state.xhr = $.ajax({
        url: options.requestUrl(mode, state.processId),
        timeout: options.timeoutMs == null ? 70000 : options.timeoutMs,
        success: function (result) {
            finishTextCommandDialog(options, state, result);
        },
        error: function (jqXHR, status, error) {
            if (status === "abort")
                return;

            failTextCommandDialog(options, state, jqXHR, status, error);
        }
    });
}

function createTextCommandDialog(options) {
    let dialog = $("#" + options.dialogId);

    if (dialog.length > 0)
        dialog.remove();

    dialog = $("<div>", {id: options.dialogId});

    let status = $("<div>", {
        id: options.statusId,
        css: {
            "font-weight": "600",
            "margin-bottom": "12px"
        }
    });

    let log = $("<div>", {
        id: options.logId,
        css: {
            "background": "#f7f7f7",
            "border": "1px solid #d8d8d8",
            "border-radius": "4px",
            "font-family": "monospace",
            "line-height": "1.45",
            "max-height": "260px",
            "min-height": "145px",
            "overflow-y": "auto",
            "padding": "10px",
            "white-space": "pre-wrap"
        }
    });

    let errorBlock = $("<pre>", {
        id: options.errorId,
        css: {
            "background": "#fff2f2",
            "border": "1px solid #d09090",
            "border-radius": "4px",
            "display": "none",
            "margin-top": "12px",
            "max-height": "180px",
            "overflow": "auto",
            "padding": "10px",
            "white-space": "pre-wrap"
        }
    });

    let actions = $("<div>", {
        css: {
            "display": "flex",
            "gap": "8px",
            "justify-content": "flex-end",
            "margin-top": "14px"
        }
    });

    let skipBtn = $("<button>", {type: "button", text: options.skip == null ? "" : options.skip.buttonText});
    let openBtn = $("<button>", {type: "button", text: options.openButtonText || "Open"}).hide();
    let downloadBtn = $("<button>", {type: "button", text: options.downloadButtonText || "Download"}).hide();

    actions.append(skipBtn, openBtn, downloadBtn);
    dialog.append(status, log, errorBlock, actions);
    $("body").append(dialog);

    let state = {
        dialog: dialog,
        status: status,
        log: log,
        errorBlock: errorBlock,
        skipBtn: skipBtn,
        openBtn: openBtn,
        downloadBtn: downloadBtn,
        resultUrl: null,
        timer: null,
        xhr: null,
        processPollStop: null,
        processId: null
    };

    openCenteredDialog(dialog, {
        appendTo: "body",
        close: function () {
            closeTextCommandDialog(state);
        },
        modal: true,
        resizable: false,
        title: options.title,
        width: Math.min(options.width || 620, $(window).width() - 40)
    });

    openBtn.on("click", function () {
        openTextCommandResult(state);
    });

    downloadBtn.on("click", function () {
        downloadTextCommandResult(options, state);
    });

    return state;
}

function centeredDialogOptions(options) {
    let originalOpen = options.open;
    let scrollLeft = $(window).scrollLeft();
    let scrollTop = $(window).scrollTop();

    return $.extend({}, options, {
        appendTo: options.appendTo || "body",
        position: options.position || {
            my: "center",
            at: "center",
            of: window
        },
        open: function (event, ui) {
            if (typeof originalOpen === "function")
                originalOpen.call(this, event, ui);

            centerDialogInViewport($(this), scrollLeft, scrollTop);
        },
        focus: function () {
            restoreWindowScroll(scrollLeft, scrollTop);
        }
    });
}

function openCenteredDialog(dialog, options) {
    ensureCenteredDialogStyle();
    dialog.dialog(centeredDialogOptions(options));
    centerDialogInViewport(dialog, $(window).scrollLeft(), $(window).scrollTop());
}

function centerDialogInViewport(dialog, scrollLeft, scrollTop) {
    setTimeout(function () {
        if (!dialog.data("ui-dialog"))
            return;

        let widget = dialog.dialog("widget");

        if (widget.length === 0 || !widget.is(":visible"))
            return;

        widget.addClass("tcbot-centered-dialog");

        restoreWindowScroll(scrollLeft, scrollTop);
    }, 0);
}

function ensureCenteredDialogStyle() {
    if ($("#tcbot-centered-dialog-style").length > 0)
        return;

    $("head").append("<style id='tcbot-centered-dialog-style'>" +
        ".tcbot-centered-dialog {" +
        "left: 50vw !important;" +
        "margin: 0 !important;" +
        "position: fixed !important;" +
        "top: 50vh !important;" +
        "transform: translate(-50%, -50%) !important;" +
        "}" +
        "</style>");
}

function restoreWindowScroll(scrollLeft, scrollTop) {
    if ($(window).scrollLeft() !== scrollLeft || $(window).scrollTop() !== scrollTop)
        window.scrollTo(scrollLeft, scrollTop);
}

function aiPromptUrlWithWaitForTc(url, waitForTc, processId) {
    return url + (url.indexOf("?") >= 0 ? "&" : "?") + "waitForTc=" + waitForTc +
        (isDefinedAndFilled(processId) ? "&processId=" + encodeURIComponent(processId) : "");
}

function startTextCommandProgress(options, state, mode, firstStep) {
    let idx = 0;
    let startedTs = Date.now();
    let hasProcessStatus = isDefinedAndFilled(state.processId);

    state.status.text(options.statusText == null ? "Running command..." : options.statusText(mode));
    state.log.empty();

    if (firstStep)
        appendTextCommandStep(state, firstStep);

    if (hasProcessStatus) {
        appendTextCommandStep(state, "Sending request to the bot REST API.");

        state.processPollStop = startBotProcessPolling(state.processId, function (status) {
            appendTextCommandStep(state, botProcessStatusText(status));
        });

        return;
    }

    function showNextStatus() {
        let message = options.progressMessage == null
            ? defaultTextCommandProgressMessage(mode, idx, Date.now() - startedTs)
            : options.progressMessage(mode, idx, Date.now() - startedTs);

        appendTextCommandStep(state, message);

        idx++;
    }

    showNextStatus();

    state.timer = setInterval(showNextStatus, options.progressIntervalMs || 5000);
}

function defaultTextCommandProgressMessage(mode, idx, elapsedMs) {
    let elapsedSec = Math.round(elapsedMs / 1000);

    if (idx === 0)
        return "Sending request to the bot server.";

    return "No response yet after " + elapsedSec + "s. Command is still running.";
}

function aiPromptProgressMessage(waitForTc, idx, elapsedMs) {
    let elapsedSec = Math.round(elapsedMs / 1000);

    if (idx === 0)
        return "Sending request to the bot server.";

    return "No prompt response yet after " + elapsedSec + "s. Waiting for the bot process status.";
}

function appendTextCommandStep(state, text) {
    state.log.children(".process-log-step").css({
        "color": "#666",
        "opacity": "0.58"
    });

    let line = $("<div>", {
        "class": "process-log-step",
        css: {
            "color": "#222",
            "opacity": "1",
            "transition": "color 0.2s ease, opacity 0.2s ease"
        }
    }).text(text);

    state.log.append(line);
    state.log.scrollTop(state.log[0].scrollHeight);
}

function finishTextCommandDialog(options, state, result) {
    if (state.timer)
        clearInterval(state.timer);

    if (state.processPollStop)
        state.processPollStop();

    if (state.resultUrl)
        URL.revokeObjectURL(state.resultUrl);

    state.resultUrl = URL.createObjectURL(new Blob([result], {
        type: options.resultMimeType || "text/plain;charset=utf-8"
    }));
    state.status.text(options.readyStatusText || "Command result is ready.");
    appendTextCommandStep(state, options.readyStepText || "Command result is ready.");
    state.skipBtn.hide();
    state.openBtn.toggle(options.showOpenButton !== false);
    state.downloadBtn.toggle(options.showDownloadButton !== false);
}

function failTextCommandDialog(options, state, jqXHR, status, error) {
    if (state.timer)
        clearInterval(state.timer);

    if (state.processPollStop)
        state.processPollStop();

    state.status.text(options.failureStatusText || "Command request failed.");
    state.skipBtn.hide();
    state.errorBlock.text((options.failureMessagePrefix || "Command request failed: ")
        + status + "\n\n" + jqXHR.responseText).show();
    appendTextCommandStep(state, "Request failed: " + (error || status));
    showErrInLoadStatus(jqXHR, status);
}

function openTextCommandResult(state) {
    if (!state.resultUrl)
        return false;

    return window.open(state.resultUrl, "_blank") != null;
}

function downloadTextCommandResult(options, state) {
    if (!state.resultUrl)
        return;

    let link = document.createElement("a");
    link.href = state.resultUrl;
    link.download = options.downloadFileName || "command-result.txt";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function closeTextCommandDialog(state) {
    if (state.timer)
        clearInterval(state.timer);

    if (state.processPollStop)
        state.processPollStop();

    if (state.xhr && state.xhr.readyState !== 4)
        state.xhr.abort();

    if (state.resultUrl)
        URL.revokeObjectURL(state.resultUrl);
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

    if (isDefinedAndFilled(result.serverVer)) {
        res += ", Jetty server: " + result.serverVer;
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
        res += "<a href=\"/\" title='Home Page'><img src='/img/leaf.svg' width='16px' height='16px'></a>";
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

        res += "<a id='userName' href='/user.html'>" + escapeHtml(userName) + "</a>";
        var logout = "/login.html" + "?exit=true&backref=" + encodeURIComponent(window.location.href);
        res += "<a href='" + logout + "'>Logout</a>";

        res += "</div>";
        res += "</div>";
    }

    $(document.body).prepend(res);
}

function renderAdminUsersList(menuData, blockSelector, usersSelector) {
    if (!menuData || menuData.admin !== true) {
        $(usersSelector).html("");
        $(blockSelector).hide();

        return;
    }

    var users = Array.isArray(menuData.users) ? menuData.users : [];
    var res = "";

    if (users.length === 0) {
        res = "No other users";
    }
    else {
        res += "<table class='stat'>";
        res += "<tr><th>User</th><th>Login</th><th>Role</th></tr>";

        for (var i = 0; i < users.length; i++) {
            var user = users[i];
            var login = user.username || "";
            var label = user.displayName || login;

            res += "<tr>";
            res += "<td><a href='/user.html?login=" + encodeURIComponent(login) + "'>" + escapeHtml(label) + "</a></td>";
            res += "<td>" + escapeHtml(login) + "</td>";
            res += "<td>" + (user.admin ? "admin" : "") + "</td>";
            res += "</tr>";
        }

        res += "</table>";
    }

    $(usersSelector).html(res);
    $(blockSelector).show();
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
