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

    function escapeHTML(s) {
        var replacements = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
        };
        return s.replace(/[&<>"']/g, function (m) { return replacements[m]; });
    }

    var source = new EventSource('/live-events');

    var pendingLogs = [];

    var tbody = document.getElementById('body');

    var msgRow = document.getElementById('last-message');
    var timeField = document.getElementById('last-message-time');
    var tagField = document.getElementById('last-message-tag');
    var pidField = document.getElementById('last-message-pid');
    var msgField = document.getElementById('last-message-message');
    var pendingMessagesCell = document.getElementById('ignoring-live');
    var pendingMessagesCount = document.getElementById('pending-msg-count');

    source.onmessage = function (ev) {
        var entry = JSON.parse(ev.data);

        pendingLogs.push(entry);

        msgRow.className = entry.level;
        timeField.innerHTML = formatTime(entry.time);
        tagField.innerHTML = entry.level + '/' + entry.tag;
        if (entry.pid === entry.tid) {
            pidField.innerHTML = '(<span title="' + escapeHTML(entry.process) + '">' + entry.pid + '</span>)';
        } else {
            pidField.innerHTML = '(<span title="' + escapeHTML(entry.process) + '">' + entry.pid + '</span>/<span title="' + escapeHTML(entry.thread) + '">' + entry.tid + '</span>)';
        }
        msgField.textContent = entry.msg;

        pendingMessagesCount.innerHTML = pendingLogs.length;
    }

    pendingMessagesCell.addEventListener('click', function (e) {
        var oldPendingLogs = pendingLogs;
        pendingLogs = [];

        var newHTML = [];
        for (var i = 0; i < oldPendingLogs.length; ++ i) {
            var entry = oldPendingLogs[i];
            newHTML.push('<tr class=');
            newHTML.push(entry.level);
            newHTML.push('><td>');
            newHTML.push(formatTime(entry.time));
            newHTML.push('<td>');
            newHTML.push(entry.level);
            newHTML.push('/');
            newHTML.push(entry.tag);
            newHTML.push('<td>(<span title="');
            newHTML.push(escapeHTML(entry.process));
            newHTML.push('">');
            newHTML.push(entry.pid);
            if (entry.pid !== entry.tid) {
                newHTML.push('</span>/<span title="');
                newHTML.push(escapeHTML(entry.thread));
                newHTML.push('">');
                newHTML.push(entry.tid);
            }
            newHTML.push('</span>)<td><pre>');
            newHTML.push(escapeHTML(entry.msg));
            newHTML.push('</pre>')
        }

        tbody.innerHTML += newHTML.join('');
    });

} else {
    // Browser is too old to support server-sent events. For now, we abort the operation.
    var cell = document.getElementById('ignoring-live');
    cell.innerHTML = messages.tooOld;
}

