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
    return hex.length == 1 ? "0" + hex : hex;
}

function rgbToHex(r, g, b) {
    return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
}

//requires element on page: <div id="loadStatus"></div>
function showErrInLoadStatus(jqXHR, exception) {
    if (jqXHR.status === 0) {
        $("#loadStatus").html('Not connect.\n Verify Network.');
    } else if (jqXHR.status == 404) {
        $("#loadStatus").html('Requested page not found. [404]');
    } else if (jqXHR.status == 401) {
        $("#loadStatus").html('Unauthorized [401]');

        setTimeout(function() {
            window.location.href = "/login.html" + "?backref=" + encodeURIComponent(window.location.href);
        }, 4000);
    } else if (jqXHR.status == 403) {
        $("#loadStatus").html('Forbidden [403]');
    } else if (jqXHR.status == 424) {
        $("#loadStatus").html('Dependency problem: [424]: ' + jqXHR.responseText);
    } else if (jqXHR.status == 500) {
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
    var res = "";
    res += "Ignite TC helper, V" + result.version + ", ";

    if (isDefinedAndFilled(result.srcWebUrl)) {
        res += "<a href='" + result.srcWebUrl + "'>source code (GitHub)</a>. ";
    }

    res += "Powered by <a href='https://ignite.apache.org/'>";
    res += "<img width='16px' height='16px' src='https://pbs.twimg.com/profile_images/568493154500747264/xTBxO73F.png'>"
    res += "Apache Ignite</a> ";

    if (isDefinedAndFilled(result.ignVer)) {
        res += "V" + result.ignVer;
    }

    $("#version").html(res);
}

$(document).ready(function() {
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
        error:  function () {
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


        res += "<div class='topnav-right'>";

        if(isDefinedAndFilled(menuData.authorizedState) && !menuData.authorizedState) {
            res += "<a onclick='authorizeServer()' href='javascript:void(0);'>Authorize Server</a>";
        }

        res += "<a href='/user.html'>"+userName+"</a>";
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
        beforeSend: function(xhr) {
            try {
                var fullTok = window.sessionStorage.getItem("token");

                if (isDefinedAndFilled(fullTok))
                    xhr.setRequestHeader("Authorization", "Token " + fullTok);
                else {
                    var fullTok = window.localStorage.getItem("token");

                    if (isDefinedAndFilled(fullTok))
                        xhr.setRequestHeader("Authorization", "Token " + fullTok);
                }
            } catch (e) {}
        }
    });
}