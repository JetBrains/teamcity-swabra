BS.HandleListener = OO.extend(BS.ErrorsAwareListener, {

  onWrongUrlError : function(elem) {
    $("errorUrl").innerHTML = elem.firstChild.nodeValue;
    this.getForm().highlightErrorField($("url"));
  },

  onCompleteSave : function(form, responseXML, err) {
    BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
    if (!err) {
      document.location.reload();
    }
  }
})

BS.HandleForm = OO.extend(BS.AbstractWebForm, {
  formElement: function() {
    return $('handleForm');
  },

  submit : function() {
    var that = this;

    window.setTimeout(function() {
      $('handleProgress').refresh();
    }, 200);

    BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.HandleListener, {
      getForm : function() {
        return that;
      }
    }));

    return false;
  }
})