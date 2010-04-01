<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="handleForm" type="jetbrains.buildServer.swabra.web.HandleForm" scope="request"/>
<jsp:useBean id="handlePathPrefix" type="java.lang.String" scope="request"/>
<jsp:useBean id="pageUrl" type="java.lang.String" scope="request"/>
<jsp:useBean id="canDownload" type="java.lang.Boolean" scope="request"/>

<c:set var="title" value="Download Handle" scope="request"/>

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

        <form id="handleForm" action="<c:url value='/handle.html'/>"
              onsubmit="return BS.HandleForm.submit()" method="post">

          <table class="runnerFormTable">
            <c:if test="${not canDownload}">
              <tr>
                <td colspan="2">
                  <div class="attentionComment">
                    <font color="red">You don't have enough permissions to download Handle.zip. Please contact your administrator.</font>
                  </div>
                </td>
              </tr>
            </c:if>
            <l:settingsGroup title="Configure URL">
              <th><label for="handleUrl">URL for downloading Handle.zip: <l:star/></label>
              </th>
              <td>
                <forms:textField name="url" id="url" style="width:30em;"
                                 value="${handleForm.url}" disabled="${not canDownload}"/>
                <span class="error" id="errorUrl"></span>

                <div class="smallNote" style="margin: 0;">
                  Url for downloading Handle.zip archive containing
                  <a showdiscardchangesmessage="false"
                     target="_blank"
                     href="http://technet.microsoft.com/en-us/sysinternals/bb896655.aspx">Handle</a>
                  excutable (handle.exe).
                  <br/>
                  On Windows agents Handle is used to determine processes which hold files in the checkout directory.
                  <br/>
                  Note that Handle will be present on agents only after the upgrade process.
                </div>
              </td>
            </l:settingsGroup>
          </table>

          <div class="saveButtonsBlock">
            <forms:cancel cameFromSupport="${handleForm.cameFromSupport}"/>
            <c:choose>
              <c:when test="${not canDownload}">
                <input class="submitButton" type="submit"
                       name="submitButton" value="Download" disabled="true"/>
              </c:when>
              <c:otherwise>
                <input class="submitButton" type="submit"
                       name="submitButton" value="Download"/>
              </c:otherwise>
            </c:choose>
            <forms:saving/>
            <input type="hidden" id="submit" name="submit"/>
            <br clear="all"/>
          </div>
        </form>

        <bs:refreshable containerId="downloadHandleMessages" pageUrl="${pageUrl}">
          <div class="downloadHandleMessagesLog">
            <c:forEach items="${handleForm.downloadHandleMessages}" var="message">
              ${message}<br/>
            </c:forEach>
          </div>
          <script type="text/javascript">
            <c:if test="${handleForm.running}">
            window.setTimeout(function() {
              $('downloadHandleMessages').refresh()
            }, 200);
            </c:if>
          </script>
        </bs:refreshable>

    </jsp:attribute>
</bs:page>
