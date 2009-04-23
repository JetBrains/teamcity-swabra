<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<jsp:useBean id="buildForm" type="jetbrains.buildServer.controllers.admin.projects.BuildTypeForm" scope="request"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="swabraModes" scope="request" class="jetbrains.buildServer.swabra.Modes"/>

<c:set var="displaySwabraSettings"
       value="${not empty propertiesBean.properties['swabra.mode'] ? true : false}"/>

<l:settingsGroup title="Swabra">

  <tr class="noBorder" id="swabra.mode.container">
    <th><label for="swabra.mode">Perform build garbage cleanup:</label></th>
    <td>
      <c:set var="onchange">
        var selectedValue = this.options[this.selectedIndex].value;
        if (selectedValue == '') {
        BS.Util.hide($('swabra.verbose.container'));
        } else {
        BS.Util.show($('swabra.verbose.container'));
        }
        $('swabra.verbose.container').disabled = (selectedValue == '');
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
      <span class="smallNote">
        Choose build garbage cleanup mode. Please note that first build after turning on this feature will entail full checkout directory cleanup. 
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
</l:settingsGroup>