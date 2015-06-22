function confirmAction(e) {
    var elem = e.target;
    var confirmInfo = elem.getAttribute('data-confirm');
    var firstComma = confirmInfo.indexOf(',');
    var action = confirmInfo.substr(0, firstComma);
    var data = confirmInfo.substr(firstComma+1);
    var message = confirmMessages[action].replace(/%s/g, data);
    if (!confirm(message)) {
        e.preventDefault();
        e.stopPropagation();
    } else {
        if (elem.tagName === 'INPUT' && elem.type === 'checkbox') {
            var pid = elem.getAttribute('data-pid');
            window.location = '/' + action + '/' + pid;
        }
    }
}

var elements = document.getElementsByClassName('needs-confirm');
for (var i = elements.length - 1; i >= 0; -- i) {
    elements[i].addEventListener('click', confirmAction);
}

