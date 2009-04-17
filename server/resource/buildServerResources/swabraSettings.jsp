<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<l:settingsGroup title="Swabra">
  <tr class="noBorder" id="swabra.enabled.container">
    <th><label for="swabra.enabled">Build garbage collector:</label></th>
    <td>
      <c:set var="onclick">
      </c:set>
      <props:checkboxProperty name="swabra.enabled" onclick="${onclick}"/><label for="coverage.enabled">Enable build garbage collector</label>
      <span class="smallNote">Select this option to enable swabbing checkout directory. Please notice, that swabbing actions can be performed only after at least one checkout directory cleaning.</span>
    </td>
  </tr>
</l:settingsGroup>