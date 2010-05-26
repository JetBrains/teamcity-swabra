BS.MultipartFormSaver = OO.extend(BS.FormSaver, {
  save : function(form, submitUrl, listener, debug) {
    var iframe = createIframe();
    var hiddenForm = createHiddenForm(form, iframe);
    var toDeleteFlag = false;

    addEvent(iframe, 'load', function() {
      if (// For Safari
          iframe.src == "javascript:'%3Chtml%3E%3C/html%3E';" ||
            // For FF, IE
              iframe.src == "javascript:'<html></html>';") {
        if (toDeleteFlag) {
          // Fix busy state in FF3
          setTimeout(function() {
            removeNode(iframe);
          }, 0);
        }
        return;
      }

      var doc = iframe.contentDocument ? iframe.contentDocument : window.frames[iframe.id].document;

      // Opera fires load event multiple times. Should not affect other browsers
      if (doc.readyState && doc.readyState != 'complete') return;
      if (doc.body && doc.body.innerHTML == "false") return;

      fillFormFromResponse(form, doc);
      removeNode(hiddenForm);
      toDeleteFlag = true;
      iframe.src = "javascript:'<html></html>';"; // Fix IE mixed content issue

      BS.FormSaver.save(form, submitUrl, listener, debug);
    });
    hiddenForm.submit();
  }
});

function createIframe() {
  var id = 'f' + Math.floor(Math.random() * 99999);

  var iframe = toElement('<iframe src="javascript:false;" name="' + id + '" />');
  iframe.setAttribute('id', id);
  iframe.style.display = 'none';
  document.body.appendChild(iframe);

  return iframe;
}

function toElement(html) {
  var div = document.createElement('div');
  div.innerHTML = html;
  var el = div.firstChild;
  return div.removeChild(el);
}

function createHiddenForm(form, iframe) {
  var hiddenForm = toElement('<form method="post" enctype="multipart/form-data"></form>');

  hiddenForm.setAttribute('action', base_uri + '/fileUpload.html');
  hiddenForm.setAttribute('target', iframe.name);
  hiddenForm.style.display = 'none';
  document.body.appendChild(hiddenForm);

  var fileInputs = Form.getInputs(form.formElement()).filter(function(element) {
    return element.getAttribute('type') == 'file' && element.name.startsWith('file:')
  });
  for (var i = 0; i < fileInputs.length; i++) {
    var real = fileInputs[i];
    var clone = real.cloneNode(true);
    real.hide();
    real.parentNode.insertBefore(clone, real);
    hiddenForm.appendChild(real);
  }

  return hiddenForm;
}

function addEvent(el, type, fn) {
  if (el.addEventListener) {
    el.addEventListener(type, fn, false);
  } else if (el.attachEvent) {
    el.attachEvent('on' + type, function() {
      fn.call(el);
    });
  } else {
    throw new Error('not supported or DOM not loaded');
  }
}

function removeNode(el) {
  el.parentNode.removeChild(el);
}

function fillFormFromResponse(form, doc) {
  var response = obtainResponse(doc);
  var fileUploadNodes = response.getElementsByTagName("uploadedFile");
  if (fileUploadNodes && fileUploadNodes.length > 0) {
    addFilesToForm(fileUploadNodes, form);
  }
}

function obtainResponse(doc) {
  if (doc.XMLDocument) {
    // response is a xml document Internet Explorer property
    return doc.XMLDocument;
  } else {
    return doc;
  }
}

function addFilesToForm(fileNodes, form) {
  var elements = Form.getInputs(form.formElement()).filter(function(element) {
    return element.getAttribute('type') == 'hidden'
  });

  for (var i = 0; i < fileNodes.length; i++) {
    var uploadedFile = fileNodes.item(i);
    var fileName = uploadedFile.getAttribute('name');
    var name = fileName.substring(5, fileName.length);  //remove "file:" prefix

    var oldVals = elements.filter(function(element) {
      return element.name == name
    });
    for (var j = 0; j < oldVals.length; j++) {
      removeNode(oldVals[j])
    }

    var input = document.createElement('input');
    input.setAttribute('type', 'hidden');
    input.setAttribute('name', name);
    if (uploadedFile.getAttribute('success') == 'true') {
      input.setAttribute('value', uploadedFile.getAttribute('path'));
    } else {
      input.setAttribute('value', '');
    }
    form.formElement().appendChild(input);
  }
}