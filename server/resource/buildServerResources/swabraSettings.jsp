<%--
  ~ Copyright 2000-2014 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="authz" tagdir="/WEB-INF/tags/authz" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="handlePresent" scope="request" type="java.lang.Boolean"/>
<jsp:useBean id="requestUrl" type="java.lang.String" scope="request"/>
<jsp:useBean id="buildTypeId" type="java.lang.String" scope="request"/>

<c:set var="enabledSelected" value="${propertiesBean.properties['swabra.enabled']}"/>
<c:set var="processesSelected" value="${propertiesBean.properties['swabra.processes']}"/>

<c:set var="displayCleanupSettings" value="${empty propertiesBean.properties['swabra.enabled'] ? false : true}"/>
<c:set var="displayProcessesSettings" value="${empty propertiesBean.properties['swabra.processes'] ? false : true}"/>

<tr>
  <td colspan="2">
    <em>Cleans checkout directory by deleting files created during the build.</em><bs:help file="Build+Files+Cleaner+(Swabra)"/>
  </td>
</tr>
<tr class="noBorder">
  <th>Files cleanup:</th>
  <td>
    <props:selectProperty name="swabra.enabled" onchange="BS.Swabra.onEnabledChange()">
      <props:option value=""
                    selected="${empty enabledSelected}">&lt;Do not clean up&gt;</props:option>
      <props:option value="swabra.before.build"
                    selected="${not empty enabledSelected && enabledSelected != 'swabra.after.build'}">Before next build start</props:option>
      <props:option value="swabra.after.build"
                    selected="${enabledSelected eq 'swabra.after.build'}">After build finish</props:option>
    </props:selectProperty>
  </td>
</tr>

<tr class="noBorder" id="swabra.strict.container"
    style="${displayCleanupSettings ? '' : 'display: none;'}">
  <th>Clean checkout:</th>
  <td>
    <props:checkboxProperty name="swabra.strict"/>
    <label for="swabra.strict">Force clean checkout if cannot restore clean directory state</label>
  </td>
</tr>

<tr class="noBorder">
  <th>Locking processes:</th>
  <td>
    <props:selectProperty name="swabra.processes" onchange="BS.Swabra.onProcessesChange()">
      <props:option value=""
                    selected="${empty processesSelected}">&lt;Do not detect&gt;</props:option>
      <props:option value="report"
                    selected="${processesSelected eq 'report'}">Report</props:option>
      <props:option value="kill"
                    selected="${processesSelected eq 'kill'}">Kill</props:option>
    </props:selectProperty>

    <span class="smallNote" id="swabra.processes.note" style="${empty processesSelected ? 'display: none;' : ''}">
      On Windows agents before the end of the build inspect the checkout directory for processes locking files in this directory.
    </span>
    <span class="smallNote" id="swabra.processes.report.note" style="${processesSelected eq 'report' ? '' : 'display: none;'}">
      Report about such processes in the build log.
      <br/>
    </span>
    <span class="smallNote" id="swabra.processes.kill.note" style="${processesSelected eq 'kill' ? '' : 'display: none;'}">
      Report about such processes in the build log and kill them.
      <br/>
    </span>
  </td>
</tr>

<tr class="noBorder" id="swabra.rules.container"
    style="${displayCleanupSettings or displayProcessesSettings? '' : 'display: none;'}">
  <th>Paths to monitor:  <bs:help file="Build+Files+Cleaner+(Swabra)"/></th>
  <td>
    <c:set var="note">
    Newline or comma delimited set of <strong>+|-:path</strong> rules.<br/>
    </c:set>
    <props:multilineProperty name="swabra.rules" rows="5" cols="40" linkTitle="Edit paths" note="${note}"/>
</tr>

<tr class="noBorder" id="swabra.verbose.container"
    style="${displayCleanupSettings ? '' : 'display: none;'}">
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

<tr class="noBorder" style="${empty processesSelected ? 'display: none;' : ''}" id="swabra.download.handle.container">
  <td colspan="2">
    <div class="${not handlePresent ? 'icon_before icon16 attentionComment' : ''}">
      <c:if test="${not handlePresent}">
        Note that for locking processes detection handle.exe tool is required on agents.<br/>
      </c:if>
      <authz:authorize allPermissions="CHANGE_SERVER_SETTINGS">
        <jsp:attribute name="ifAccessGranted">
          <c:url var="handleDownloader" value="/admin/admin.html?item=toolInstallTab&toolType=handleTool"/>
          <a href="${handleDownloader}" target="_blank" showdiscardchangesmessage="false">${actionName} Sysinternals handle.exe</a>
        </jsp:attribute>
        <jsp:attribute name="ifAccessDenied">
          <c:if test="${not handlePresent}">
            Please ask your System Administrator to ${fn:toLowerCase(actionName)} Sysinternals handle.exe using Administration -> Tools page.
          </c:if>
        </jsp:attribute>
      </authz:authorize>
    </div>
  </td>
</tr>

<script type="text/javascript">
  BS.Swabra = {
    onEnabledChange: function() {
      var enabledEl = $('swabra.enabled');
      var enabledSelectedValue = enabledEl.options[enabledEl.selectedIndex].value;

      if (enabledSelectedValue == '') {
        BS.Util.hide('swabra.strict.container');
        BS.Util.hide('swabra.verbose.container');

        var processesEl = $('swabra.processes');
        var processesSelectedValue = processesEl.options[processesEl.selectedIndex].value;

        if (processesSelectedValue == '') {
          BS.Util.hide('swabra.rules.container');
        }
      } else {
        BS.Util.show('swabra.strict.container');
        BS.Util.show('swabra.verbose.container');
        BS.Util.show('swabra.rules.container');
      }

      BS.MultilineProperties.updateVisible();
    },

    onProcessesChange: function() {
      var processesEl = $('swabra.processes');
      var processesSelectedValue = processesEl.options[processesEl.selectedIndex].value;

      if (processesSelectedValue == '') {
        BS.Util.hide('swabra.processes.note');
        BS.Util.hide('swabra.processes.report.note');
        BS.Util.hide('swabra.processes.kill.note');
        BS.Util.hide('swabra.processes.handle.note');
        BS.Util.hide('swabra.download.handle.container');

        var enabledEl = $('swabra.enabled');
        var enabledSelectedValue = enabledEl.options[enabledEl.selectedIndex].value;

        if (enabledSelectedValue == '') {
          BS.Util.hide('swabra.rules.container');
        }
      } else {
        BS.Util.show('swabra.processes.note');
        BS.Util.show('swabra.processes.handle.note');
        BS.Util.show('swabra.download.handle.container');
        BS.Util.show('swabra.rules.container');

        if (processesSelectedValue == 'report') {
          BS.Util.show('swabra.processes.report.note');
          BS.Util.hide('swabra.processes.kill.note');
        } else if (processesSelectedValue == 'kill') {
          BS.Util.hide('swabra.processes.report.note');
          BS.Util.show('swabra.processes.kill.note');
        }
      }
      BS.MultilineProperties.updateVisible();
    }
  };
</script>



