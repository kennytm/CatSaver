function togglePurgeCheckbox(e) {
    var elem = e.target;
    var targetElemName = elem.name.match(/^purge-by-(\w+)$/)[1];
    var targetElem = document.getElementsByName(targetElemName)[0];
    targetElem.disabled = !elem.checked;
}

var elements = document.getElementsByClassName('purge-checkbox');
for (var i = elements.length - 1; i >= 0; -- i) {
    elements[i].addEventListener('click', togglePurgeCheckbox);
}

