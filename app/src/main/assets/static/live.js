var tbody = document.getElementById('body');

if (!!window.EventSource) {
    function formatTime(milliseconds) {
        var date = new Date(milliseconds);
        var hours = ('00' + date.getHours()).slice(-2);
        var minutes = ('00' + date.getMinutes()).slice(-2);
        var seconds = ('00' + date.getSeconds()).slice(-2);
        var ms = ('000' + date.getMilliseconds()).slice(-3);
        return hours + ':' + minutes + ':' + seconds + '.' + ms;
    }

    // Ref: Check if we are at bottom using http://stackoverflow.com/a/22394544/224671
    function checkIsBottom() {
        var scrollTop = (document.documentElement && document.documentElement.scrollTop) || document.body.scrollTop;
        var scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;
        return (scrollTop + window.innerHeight) >= scrollHeight;
    }

    function scrollToBottom() {
        var scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;
        var scrollLeft = (document.documentElement && document.documentElement.scrollLeft) || document.body.scrollLeft;
        window.scrollTo(scrollLeft, scrollHeight);
    }

    var source = new EventSource('/live-events');
    source.onmessage = function (ev) {
        var isBottom = checkIsBottom();
        var entry = JSON.parse(ev.data);

        var row = tbody.insertRow();
        row.className = entry.level;

        var dateCell = row.insertCell();
        dateCell.innerHTML = formatTime(entry.time);

        var tagCell = row.insertCell();
        tagCell.innerHTML = entry.level + '/' + entry.tag;

        var pidCell = row.insertCell();
        if (entry.pid === entry.tid) {
            pidCell.innerHTML = '(' + entry.pid + ')';
        } else {
            pidCell.innerHTML = '(' + entry.pid + '/' + entry.tid + ')';
        }

        var msgCell = row.insertCell();
        var preTag = document.createElement('pre');
        preTag.textContent = entry.msg;
        msgCell.appendChild(preTag);

        if (isBottom) {
            scrollToBottom();
        }
    }

} else {
    // Browser is too old to support server-sent events. For now, we abort the operation.
    var row = tbody.insertRow();
    var cell = row.insertCell();
    cell.rowSpan = 4;
    cell.innerHTML = errorMessages.tooOld;
}

