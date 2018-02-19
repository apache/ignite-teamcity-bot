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