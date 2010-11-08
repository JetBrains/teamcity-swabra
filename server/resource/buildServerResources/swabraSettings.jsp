<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<%@ page import="jetbrains.buildServer.swabra.HandleProvider" %>

<c:set var="handlePresent"><%=HandleProvider.isHandlePresent()%></c:set>
<c:set var="selected" value="${propertiesBean.properties['swabra.processes']}"/>
<c:set var="displaySwabraSettings" value="${empty propertiesBean.properties['swabra.enabled'] ? false : true}"/>

<tr>
  <td colspan="2">
    <em>Swabra build feature cleans files created during the build.</em>
  </td>
</tr>
<tr class="noBorder">
  <th>Build files cleanup:</th>
  <td>
    <c:set var="onclick">
      if (this.checked) {
      BS.Util.show($('swabra.strict.container'));
      BS.Util.show($('swabra.verbose.container'));
      BS.Util.show($('swabra.rules.container'));
      } else {
      BS.Util.hide($('swabra.strict.container'));
      BS.Util.hide($('swabra.verbose.container'));
      BS.Util.hide($('swabra.rules.container'));
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

<tr class="noBorder" id="swabra.rules.container"
    style="${displaySwabraSettings ? '' : 'display: none;'}">
  <th>Cleanup rules:</th>
  <td>
      <props:multilineProperty name="swabra.rules" expanded="${not empty propertiesBean.properties['swabra.rules']}" rows="5" cols="40"
                               linkTitle="Edit cleanup rules"/>
    <div class="smallNote" style="margin-left: 0;">
      Newline or comma delimited set of <strong>+|-:relative_path</strong> rules.<br/>
      By default all paths are included. Rules on any path should come in order from more abstract to more concrete,
      e.g. use <strong>-:**/dir/**</strong> to exclude all <strong>dir</strong> folders and their content,
      or <strong>-:some/dir, +:some/dir/inner</strong> to exclude <strong>some/dir</strong> folder and all it's content
      except <strong>inner</strong> subfolder and it's content.<br/>
    </div>
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

<c:choose>
  <c:when test="${not handlePresent}">
    <c:set var="actionName" value="Install"/>
  </c:when>
  <c:otherwise>
    <c:set var="actionName" value="Update"/>
  </c:otherwise>
</c:choose>
<tr class="noBorder" id="swabra.download.handle.container" style="${empty selected ? 'display: none;' : ''}">
  <th>
  </th>
  <td>
    <c:url var="handleDownloader" value="/admin/handle.html"/>
    <a href="${handleDownloader}" target="_blank" showdiscardchangesmessage="false">${actionName} SysInternals handle.exe</a>
  </td>
</tr>
