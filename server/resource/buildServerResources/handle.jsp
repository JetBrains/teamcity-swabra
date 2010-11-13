 <%--Copyright 2000-2010 JetBrains s.r.o.--%>

 <%--Licensed under the Apache License, Version 2.0 (the "License");--%>
 <%--you may not use this file except in compliance with the License.--%>
 <%--You may obtain a copy of the License at--%>

 <%--http://www.apache.org/licenses/LICENSE-2.0--%>

 <%--Unless required by applicable law or agreed to in writing, software--%>
 <%--distributed under the License is distributed on an "AS IS" BASIS,--%>
 <%--WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.--%>
 <%--See the License for the specific language governing permissions and--%>
 <%--limitations under the License.--%>

<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="handleForm" type="jetbrains.buildServer.swabra.web.HandleForm" scope="request"/>
<jsp:useBean id="handlePathPrefix" type="java.lang.String" scope="request"/>
<jsp:useBean id="pageUrl" type="java.lang.String" scope="request"/>
<jsp:useBean id="canLoad" type="java.lang.Boolean" scope="request"/>

<c:set var="title" value="Load Handle" scope="request"/>

<bs:page>
    <jsp:attribute name="head_include">
        <bs:linkCSS>
          /css/forms.css
          /css/project.css
          /css/admin/adminMain.css
          /css/admin/projectForm.css
          /css/admin/vcsRootsTable.css
          /css/admin/reportTabs.css
        </bs:linkCSS>

        <style type="text/css">
          .handleLog .errorMessage {
            color: red;
          }
        </style>

        <bs:linkScript>
          /js/bs/blocks.js
          /js/bs/blocksWithHeader.js
          /js/bs/forms.js
          /js/bs/multipart.js
        </bs:linkScript>

        <script type="text/javascript" src="${handlePathPrefix}handle.js"></script>

        <script type="text/javascript">
          BS.Navigation.items = [
            {title: "Server Configuration", url: '<c:url value="/admin/serverConfig.html?init=1"/>'},
            {title: "${title}", selected:true}
          ];
        </script>
    </jsp:attribute>

    <jsp:attribute name="body_include">
      <div style="width: 80%;">
        <form id="handleForm" action="${pageUrl}" onsubmit="return BS.HandleForm.submit();" method="post">

          <table class="runnerFormTable">
            <c:if test="${not canLoad}">
              <tr>
                <td colspan="2">
                  <div class="attentionComment">
                    <font color="red">You don't have enough permissions to download Handle.zip. Please contact your administrator.</font>
                  </div>
                </td>
              </tr>
              <c:set var="disableFileUpload">disbaled="disabled"</c:set>
            </c:if>

            <l:settingsGroup title="Load handle.exe">
              <tr>
                <th><label for="loadType">Load handle.exe:</label></th>
                <td>
                  <c:set var="onclick">
                    if ($('upload').checked) {
                    BS.Util.show($('uploadSettingsContainer'));
                    BS.Util.hide($('downloadSettingsContainer'));
                    } else {
                    BS.Util.hide($('uploadSettingsContainer'));
                    BS.Util.show($('downloadSettingsContainer'));
                    }
                  </c:set>
                  <forms:radioButton name="loadType" id="upload"
                                     checked="${handleForm.loadType == 'UPLOAD'}"
                                     onclick="${onclick}" value="UPLOAD"/>
                  <label for="upload" style="float:none;">Upload</label><br/>

                  <forms:radioButton name="loadType" id="download"
                                     checked="${handleForm.loadType == 'DOWNLOAD'}"
                                     onclick="${onclick}" value="DOWNLOAD"/>
                  <label for="download" style="float:none;">Download</label><br/>
                </td>
              </tr>

              <tr id="uploadSettingsContainer" style="${handleForm.loadType == 'UPLOAD' ? '' : 'display: none;'}">
                <th><label for="file:handleFile">Path for uploading: <l:star/></label></th>
                <td>
                  <c:set var="attrs">size="60" ${disableFileUpload}</c:set>
                  <forms:file name="handleFile" attributes="${attrs}"/>
                  <span class="error" id="errorHandleFile"></span>

                  <div class="smallNote" style="margin: 0;">
                    Path for uploading Sysinternals
                    <a showdiscardchangesmessage="false"
                       target="_blank"
                       href="http://technet.microsoft.com/en-us/sysinternals/bb896655.aspx">handle.exe</a>.
                    <br/>
                    On Windows agents handle.exe is used to determine processes which hold files in the checkout directory.
                    <br/>
                    handle.exe will be present on agents only after the upgrade process.
                  </div>
                </td>
              </tr>

              <tr id="downloadSettingsContainer" style="${handleForm.loadType == 'DOWNLOAD' ? '' : 'display: none;'}">
                <th><label for="handleUrl">URL for downloading: <l:star/></label></th>
                <td>
                  <forms:textField name="url" id="url" style="width:30em;"
                                   value="${handleForm.url}" disabled="${not canLoad}"/>
                  <span class="error" id="errorUrl"></span>

                  <div class="smallNote" style="margin: 0;">
                    Url for downloading Sysinternals
                    <a showdiscardchangesmessage="false"
                       target="_blank"
                       href="http://technet.microsoft.com/en-us/sysinternals/bb896655.aspx">handle.exe</a>.
                    <br/>
                    On Windows agents handle.exe is used to determine processes which hold files in the checkout directory.
                    <br/>
                    Note that by pressing "Load" button you accept the
                    <a showdiscardchangesmessage="false"
                       target="_blank"
                       href="http://technet.microsoft.com/en-us/sysinternals/bb469936.aspx">Sysinternals Software License Terms</a>.
                    <br/>
                    handle.exe will be present on agents only after the upgrade process.
                  </div>
                </td>
              </tr>
            </l:settingsGroup>
          </table>

          <div class="saveButtonsBlock">
            <forms:cancel cameFromSupport="${handleForm.cameFromSupport}"/>
            <c:choose>
              <c:when test="${not canLoad}">
                <input class="submitButton" type="submit"
                       name="submitButton" value="Load" disabled="true"/>
              </c:when>
              <c:otherwise>
                <input class="submitButton" type="submit"
                       name="submitButton" value="Load"/>
              </c:otherwise>
            </c:choose>
            <forms:saving/>
            <input type="hidden" id="submit" name="submit"/>
            <br clear="all"/>
          </div>
        </form>

        <bs:refreshable containerId="loadHandleMessages" pageUrl="${pageUrl}">
          <div class="loadHandleMessagesLog">
            <c:forEach items="${handleForm.loadHandleMessages}" var="message">
              ${message}<br/>
            </c:forEach>
          </div>
          <script type="text/javascript">
            <c:if test="${handleForm.running}">
            window.setTimeout(function() {
              $('loadHandleMessages').refresh()
            }, 200);
            </c:if>
          </script>
        </bs:refreshable>
      </div>
    </jsp:attribute>
</bs:page>
