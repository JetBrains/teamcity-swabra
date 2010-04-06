<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="swabraModes" scope="request" class="jetbrains.buildServer.swabra.SwabraModes"/>

<%@ page import="jetbrains.buildServer.swabra.HandleProvider" %>
<c:set var="handlePresent"><%=HandleProvider.isHandlePresent()%>
</c:set>

<c:set var="displaySwabraSettings"
       value="${not empty propertiesBean.properties['swabra.mode'] ? true : false}"/>

<c:set var="displayBeforeBuildSwabraSettings"
       value="${propertiesBean.properties['swabra.mode'] == 'swabra.before.build' ? true : false}"/>

<c:set var="displayAfterBuildSwabraSettings"
       value="${propertiesBean.properties['swabra.mode'] == 'swabra.after.build' ? true : false}"/>

<c:set var="displayLockingProcessesSettings"
       value="${not empty propertiesBean.properties['swabra.locking.processes'] ? true : false}"/>

<l:settingsGroup title="Swabra">

  <tr class="noBorder" id="swabra.mode.container">
    <th><label for="swabra.mode">Perform build files cleanup:</label></th>
    <td>
      <c:set var="onchange">
        var selectedValue = this.options[this.selectedIndex].value;
        if (selectedValue == 'swabra.before.build') {
        BS.Util.show($('swabra.verbose.container'));
        BS.Util.show($('swabra.strict.container'));
        BS.Util.show($('swabra.kill.container'));

        BS.Util.hide($('swabra.mode.note'));
        BS.Util.show($('swabra.before.build.mode.note'));
        BS.Util.hide($('swabra.after.build.mode.note'));

        } else {
        if (selectedValue == 'swabra.after.build') {
        BS.Util.hide($('swabra.verbose.container'));
        BS.Util.show($('swabra.strict.container'));
        BS.Util.show($('swabra.kill.container'));

        BS.Util.hide($('swabra.mode.note'));
        BS.Util.hide($('swabra.before.build.mode.note'));
        BS.Util.show($('swabra.after.build.mode.note'));
        } else {
        BS.Util.hide($('swabra.verbose.container'));
        BS.Util.hide($('swabra.strict.container'));
        BS.Util.hide($('swabra.kill.container'));

        BS.Util.show($('swabra.mode.note'));
        BS.Util.hide($('swabra.before.build.mode.note'));
        BS.Util.hide($('swabra.after.build.mode.note'));
        }
        }
        BS.MultilineProperties.updateVisible();
      </c:set>
      <props:selectProperty name="swabra.mode"
                            onchange="${onchange}">
        <c:set var="selected" value="false"/>
        <c:if test="${empty propertiesBean.properties['swabra.mode']}">
          <c:set var="selected" value="true"/>
        </c:if>
        <props:option value="" selected="${selected}">&lt;Do not cleanup&gt;</props:option>
        <c:forEach var="mode" items="${swabraModes.modes}">
          <c:set var="selected" value="false"/>
          <c:if test="${mode.id == propertiesBean.properties['swabra.mode']}">
            <c:set var="selected" value="true"/>
          </c:if>
          <props:option value="${mode.id}"
                        selected="${selected}"><c:out value="${mode.displayName}"/></props:option>
        </c:forEach>
      </props:selectProperty>
      <span class="smallNote" id="swabra.mode.note" style="${displaySwabraSettings ? 'display: none;' : ''}">
        Choose cleanup mode.
      </span>
    <span class="smallNote" id="swabra.before.build.mode.note"
          style="${displayBeforeBuildSwabraSettings ? '' : 'display: none;'}">
        Previous build files cleanup will be performed at build start. You only need to use this mode
        if files are required between builds.
    </span>
    <span class="smallNote" id="swabra.after.build.mode.note"
          style="${displayAfterBuildSwabraSettings ? '' : 'display: none;'}">
        Build files cleanup will be performed after the build. Between builds there will be clean copy in the checkout directory.
    </span>
    </td>
  </tr>

  <tr class="noBorder" id="swabra.strict.container"
      style="${displaySwabraSettings ? '' : 'display: none;'}">
    <th>Strict mode:</th>
    <td>
      <props:checkboxProperty name="swabra.strict"/>
      <label for="swabra.strict">Turn on strict mode</label>
            <span class="smallNote">
              Ensure that at the build start the checkout directory corresponds to the sources in the repository, otherwise perform clean checkout.</span>
    </td>
  </tr>

  <tr class="noBorder" id="swabra.kill.container"
      style="${displaySwabraSettings ? '' : 'display: none;'}">
    <th>Locking processes kill:</th>
    <td>
      <c:set var="onclick">
        if (this.checked) {
        BS.Util.show($('swabra.download.handle.container'));
        } else {
        BS.Util.hide($('swabra.download.handle.container'));
        }
        BS.MultilineProperties.updateVisible();
      </c:set>
      <props:checkboxProperty name="swabra.kill" onclick="${onclick}"/>
      <label for="swabra.kill">Kill file locking processes on Windows agents</label>
            <span class="smallNote">
              When Swabra comes across a newly created file which is locked it tries to kill the locking process.
              Note that handle.exe is required on agents.
            </span>
    </td>
  </tr>

  <tr class="noBorder" id="swabra.locking.processes.container">
    <th>Locking processes detection:</th>
    <td>
      <c:set var="onclick">
        if (this.checked) {
        BS.Util.show($('swabra.download.handle.container'));
        } else {
        BS.Util.hide($('swabra.download.handle.container'));
        }
        BS.MultilineProperties.updateVisible();
      </c:set>
      <props:checkboxProperty name="swabra.locking.processes" onclick="${onclick}"/>
      <label for="swabra.locking.processes">Determine file locking processes on Windows agents</label>
      <span class="smallNote">
        Before the end of the build the checkout directory is checked for locking processes. The checking results are present in the build log.
        Note that handle.exe is required on agents.
      </span>
    </td>
  </tr>

  <c:if test="${not handlePresent}">
    <tr class="noBorder" id="swabra.download.handle.container"
        style="${displayLockingProcessesSettings ? '' : 'display: none;'}">
      <th>
      </th>
      <td>
        <a href="handle.html"
           showdiscardchangesmessage="true"
           target="_blank"
           title="Download Handle executable for locking processes detection">Download handle.exe</a>
      </td>
    </tr>
  </c:if>

  <tr class="noBorder" id="swabra.verbose.container"
      style="${displayBeforeBuildSwabraSettings ? '' : 'display: none;'}">
    <th><label for="swabra.verbose">Verbose output:</label></th>
    <td>
      <props:checkboxProperty name="swabra.verbose"/>
    </td>
  </tr>
</l:settingsGroup>
