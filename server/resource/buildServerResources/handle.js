/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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