<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<%@ page import="jetbrains.buildServer.swabra.HandleProvider" %>

<c:set var="handlePresent"><%=HandleProvider.isHandlePresent()%>
</c:set>

<c:set var="selected"
       value="${propertiesBean.properties['swabra.processes']}"/>

<c:set var="displaySwabraSettings"
       value="${empty propertiesBean.properties['swabra.enabled'] ? false : true}"/>

<l:settingsGroup title="Swabra">

  <tr class="noBorder">
    <th>Build files cleanup:</th>
    <td>
      <c:set var="onclick">
        if (this.checked) {
        BS.Util.show($('swabra.strict.container'));
        BS.Util.show($('swabra.verbose.container'));
        BS.Util.show($('swabra.ignored.container'));
        } else {
        BS.Util.hide($('swabra.strict.container'));
        BS.Util.hide($('swabra.verbose.container'));
        BS.Util.hide($('swabra.ignored.container'));
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

  <tr class="noBorder" id="swabra.ignored.container"
      style="${displaySwabraSettings ? '' : 'display: none;'}">
    <th>Paths to ignore:</th>
    <td>
      <props:multilineProperty name="swabra.ignored" expanded="${not empty propertiesBean.properties['swabra.ignored']}" rows="5" cols="50"
                               linkTitle="Type paths to ignore"/>
      <span class="smallNote">
        New line or comma separated paths which will be ignored while running build files cleanup.
        Support ant-style wildcards like <strong>dir/**/?*.jar</strong>.</span>
    </td>
  </tr>

  <tr class="noBorder">
    <th>Locking processes:</th>
    <td>
      <c:set var="onchange">
        var selectedValue = this.options[this.selectedIndex].value;
        if (selectedValue == '') {
        BS.Util.hide($('swabra.processes.note'));
        BS.Util.hide($('swabra.processes.report.note'));
        BS.Util.hide($('swabra.processes.kill.note'));
        BS.Util.hide($('swabra.processes.handle.note'));
        BS.Util.hide($('swabra.download.handle.container'));
        } else {
        BS.Util.show($('swabra.processes.note'));
        BS.Util.show($('swabra.processes.handle.note'));
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
      <props:selectProperty name="swabra.processes" onchange="${onchange}">
        <props:option value=""
                      selected="${empty selected}">&lt;Do not detect&gt;</props:option>
        <props:option value="report"
                      selected="${selected == 'report'}">Report</props:option>
        <props:option value="kill"
                      selected="${selected == 'kill'}">Kill</props:option>
      </props:selectProperty>

      <span class="smallNote" id="swabra.processes.note" style="${empty selected ? 'display: none;' : ''}">
        Before the end of the build inspect the checkout directory for processes locking files in this directory.
      </span>  
      <span class="smallNote" id="swabra.processes.report.note" style="${selected == 'report' ? '' : 'display: none;'}">
        Report about such processes in the build log.
        <br/>
      </span>
      <span class="smallNote" id="swabra.processes.kill.note" style="${selected == 'kill' ? '' : 'display: none;'}">
        Report about such processes in the build log and kill them.
        <br/>
      </span>
      <c:if test="${not handlePresent}">
        <span class="smallNote" id="swabra.processes.handle.note" style="${empty selected ? 'display: none;' : ''}">
          Note that handle.exe is required on agents.
        </span>
      </c:if>
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
    <tr class="noBorder" id="swabra.download.handle.container" style="${empty selected ? 'display: none;' : ''}">
      <th>
      </th>
      <td>
        <c:url var="handleDownloader" value="/admin/handle.html"/>
        <input type="button" value="Install SysInternals handle.exe" onclick="window.open('${handleDownloader}', '_blank')"/>
      </td>
    </tr>
  </c:if>
</l:settingsGroup>
