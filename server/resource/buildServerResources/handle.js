BS.HandleListener = OO.extend(BS.ErrorsAwareListener, {
  onBeginSave : function(form) {
    BS.ErrorsAwareListener.onBeginSave(form);

    window.setTimeout(function() {
      $('loadHandleMessages').refresh();
    }, 200);
  },

  onWrongUrlError : function(elem) {
    $("errorUrl").innerHTML = elem.firstChild.nodeValue;
    this.getForm().highlightErrorField($("url"));
  },

  onWrongFileError : function(elem) {
    $("errorHandleFile").innerHTML = elem.firstChild.nodeValue;
    this.getForm().highlightErrorField($("file:handleFile"));
  },

  onCompleteSave : function(form, responseXML, err) {
    BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
    $('loadHandleMessages').refresh();
    if (!err) {
      document.location.reload();
    }
  }
});

BS.HandleForm = OO.extend(BS.AbstractWebForm, {
  formElement: function() {
    return $('handleForm');
  },

  submit : function() {
    var that = this;

    BS.MultipartFormSaver.save(that, that.formElement().action, OO.extend(BS.HandleListener, {
      getForm : function() {
        return that;
      }
    }));
    return false;
  }
});