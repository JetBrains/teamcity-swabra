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

 <%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<%@ page import="jetbrains.buildServer.swabra.HandleProvider" %>

<c:set var="handlePresent"><%=HandleProvider.isHandlePresent()%></c:set>
<c:set var="enabledSelected" value="${propertiesBean.properties['swabra.enabled']}"/>
<c:set var="processesSelected" value="${propertiesBean.properties['swabra.processes']}"/>
<c:set var="displaySwabraSettings" value="${empty propertiesBean.properties['swabra.enabled'] ? false : true}"/>

<tr>
  <td colspan="2">
    <em>Cleans checkout directory by deleting files created during the build.</em>
  </td>
</tr>
<tr class="noBorder">
  <th>Files cleanup:</th>
  <td>
    <c:set var="onchange">
      var selectedValue = this.options[this.selectedIndex].value;
      if (selectedValue == '') {
      BS.Util.hide($('swabra.strict.container'));
      BS.Util.hide($('swabra.verbose.container'));
      BS.Util.hide($('swabra.rules.container'));
      } else {
      BS.Util.show($('swabra.strict.container'));
      BS.Util.show($('swabra.verbose.container'));
      BS.Util.show($('swabra.rules.container'));
      }
      BS.MultilineProperties.updateVisible();
    </c:set>
    <props:selectProperty name="swabra.enabled" onchange="${onchange}">
      <props:option value=""
                    selected="${empty enabledSelected}">&lt;Do not cleanup&gt;</props:option>
      <props:option value="swabra.before.build"
                    selected="${not empty enabledSelected && enabledSelected != 'swabra.after.build'}">Before next build start</props:option>
      <props:option value="swabra.after.build"
                    selected="${enabledSelected == 'swabra.after.build'}">After build finish</props:option>
    </props:selectProperty>
  </td>
</tr>

<tr class="noBorder" id="swabra.strict.container"
    style="${displaySwabraSettings ? '' : 'display: none;'}">
  <th>Clean checkout:</th>
  <td>
    <props:checkboxProperty name="swabra.strict"/>
    <label for="swabra.strict">Force clean checkout if cannot restore clean directory state</label>
  </td>
</tr>

<tr class="noBorder" id="swabra.rules.container"
    style="${displaySwabraSettings ? '' : 'display: none;'}">
  <th>Paths to monitor: </th>
  <td>
      <props:multilineProperty name="swabra.rules" expanded="${not empty propertiesBean.properties['swabra.rules']}" rows="5" cols="40"
                               linkTitle="Edit paths"/>
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
                    selected="${empty processesSelected}">&lt;Do not detect&gt;</props:option>
      <props:option value="report"
                    selected="${processesSelected == 'report'}">Report</props:option>
      <props:option value="kill"
                    selected="${processesSelected == 'kill'}">Kill</props:option>
    </props:selectProperty>

    <span class="smallNote" id="swabra.processes.note" style="${empty processesSelected ? 'display: none;' : ''}">
      Before the end of the build inspect the checkout directory for processes locking files in this directory.
    </span>
    <span class="smallNote" id="swabra.processes.report.note" style="${processesSelected == 'report' ? '' : 'display: none;'}">
      Report about such processes in the build log.
      <br/>
    </span>
    <span class="smallNote" id="swabra.processes.kill.note" style="${processesSelected == 'kill' ? '' : 'display: none;'}">
      Report about such processes in the build log and kill them.
      <br/>
    </span>
    <c:if test="${not handlePresent}">
      <span class="smallNote" id="swabra.processes.handle.note" style="${empty processesSelected ? 'display: none;' : ''}">
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
<tr class="noBorder" id="swabra.download.handle.container" style="${empty processesSelected ? 'display: none;' : ''}">
  <th>
  </th>
  <td>
    <c:url var="handleDownloader" value="/admin/handle.html"/>
    <a href="${handleDownloader}" target="_blank" showdiscardchangesmessage="false">${actionName} SysInternals handle.exe</a>
  </td>
</tr>
