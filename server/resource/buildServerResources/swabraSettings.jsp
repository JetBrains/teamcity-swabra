<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<%@ page import="jetbrains.buildServer.swabra.HandleProvider" %>
<c:set var="handlePresent"><%=HandleProvider.isHandlePresent()%>
</c:set>

<c:set var="displaySwabraSettings"
       value="${empty propertiesBean.properties['swabra.enabled'] ? false : true}"/>

<%--<c:set var="updateHandleDownloader">--%>
<%--if ($('swabra.kill').checked || $('swabra.locking.processes').checked) {--%>
<%--BS.Util.show($('swabra.download.handle.container'));--%>
<%--} else {--%>
<%--BS.Util.hide($('swabra.download.handle.container'));--%>
<%--}--%>
<%--BS.MultilineProperties.updateVisible();--%>
<%--</c:set>--%>

<l:settingsGroup title="Swabra">

  <tr class="noBorder">
    <th>Build files cleanup:</th>
    <td>
      <c:set var="onclick">
        if (this.checked) {
        BS.Util.show($('swabra.strict.container'));
        BS.Util.show($('swabra.verbose.container'));
        } else {
        BS.Util.hide($('swabra.strict.container'));
        BS.Util.hide($('swabra.verbose.container'));
        }
        BS.MultilineProperties.updateVisible();
      </c:set>
      <props:checkboxProperty name="swabra.enabled" onclick="${onclick}"/>
      <label for="swabra.enabled">Perform build files cleanup</label>
      <span class="smallNote">
        At the build start inspect the checkout directory for files created, modified and deleted during previous build.</span>
    </td>
  </tr>

  <tr class="noBorder" id="swabra.strict.container"
      style="${displaySwabraSettings ? '' : 'display: none;'}">
    <th>Clean checkout directory:</th>
    <td>
      <props:checkboxProperty name="swabra.strict"/>
      <label for="swabra.strict">Ensure clean checkout directory</label>
            <span class="smallNote">
              Ensure that at the build start the checkout directory corresponds to the sources in the repository, otherwise perform clean checkout.</span>
    </td>
  </tr>

  <%--<tr class="noBorder" id="swabra.kill.container"--%>
  <%--style="${displaySwabraSettings ? '' : 'display: none;'}">--%>
  <%--<th>Locking processes kill:</th>--%>
  <%--<td>--%>
  <%--<props:checkboxProperty name="swabra.kill" onclick="${updateHandleDownloader}"/>--%>
  <%--<label for="swabra.kill">Kill file locking processes on Windows agents</label>--%>
  <%--<span class="smallNote">--%>
  <%--When Swabra comes across a newly created file which is locked it tries to kill the locking process.<br/>--%>
  <%--Note that handle.exe is required on agents.--%>
  <%--</span>--%>
  <%--</td>--%>
  <%--</tr>--%>

  <%--<tr class="noBorder" id="swabra.locking.processes.container">--%>
  <%--<th>Locking processes detection:</th>--%>
  <%--<td>--%>
  <%--<props:checkboxProperty name="swabra.locking.processes" onclick="${updateHandleDownloader}"/>--%>
  <%--<label for="swabra.locking.processes">Determine file locking processes on Windows agents</label>--%>
  <%--<span class="smallNote">--%>
  <%--Before the end of the build the checkout directory is inspected for locking processes.<br/>--%>
  <%--Note that handle.exe is required on agents.--%>
  <%--</span>--%>
  <%--</td>--%>
  <%--</tr>--%>

  <tr class="noBorder">
    <th>Locking processes:</th>
    <td>
      <c:set var="onchange">
        var selectedValue = this.options[this.selectedIndex].value;
        if (selectedValue == '') {
        BS.Util.hide($('swabra.download.handle.container'));
        } else {
        BS.Util.show($('swabra.download.handle.container'));
        if (selectedValue == 'report') {
        BS.Util.show($('swabra.processes.report.note'));
        BS.Util.hide($('swabra.processes.kill.note'));
        } else if (selectedValue == 'kill') {
        BS.Util.hide($('swabra.processes.report.note'));
        BS.Util.show($('swabra.processes.kill.note'));
        }
        }
        BS.MultilineProperties.updateVisible();
      </c:set>
      <c:set var="selected"
             value="${propertiesBean.properties['swabra.processes']}"/>
      <props:selectProperty name="swabra.processes" onchange="${onchange}">
        <%--<props:option value=""--%>
        <%--selected="${empty selected}">&lt;Do not detect&gt;</props:option>--%>
        <props:option value="report"
                      selected="${selected == 'report'}">Report</props:option>
        <props:option value="kill"
                      selected="${selected == 'kill'}">Kill</props:option>
      </props:selectProperty>
      <span class="smallNote">
        Before the end of the build inspect the checkout directory for processes locking files in this directory.
      </span>  
      <span class="smallNote" id="swabra.processes.report.note" style="${selected == 'report' ? '' : 'display: none;'}">
        Report about such processes in the build log.
        <br/>
        Note that handle.exe is required on agents.
      </span>
      <span class="smallNote" id="swabra.processes.kill.note" style="${selected == 'kill' ? '' : 'display: none;'}">
        Report about such processes in the build log and kill them.
        <br/>
        Note that handle.exe is required on agents.
    </span>
    </td>
  </tr>

  <tr class="noBorder" id="swabra.verbose.container"
      style="${displaySwabraSettings ? '' : 'display: none;'}">
    <th><label for="swabra.verbose">Verbose output:</label></th>
    <td>
      <props:checkboxProperty name="swabra.verbose"/>
    </td>
  </tr>

  <c:if test="${not handlePresent}">
    <tr class="noBorder" id="swabra.download.handle.container">
      <th>
      </th>
      <td>
        <c:url var="handleDownloader" value="/admin/handle.html"/>
        <input type="button" value="Install SysInternals handle.exe" onclick="window.open('${handleDownloader}', '_blank')"/>
      </td>
    </tr>
  </c:if>
</l:settingsGroup>
