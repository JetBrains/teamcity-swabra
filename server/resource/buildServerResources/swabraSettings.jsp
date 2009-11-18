<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<jsp:useBean id="buildForm" type="jetbrains.buildServer.controllers.admin.projects.BuildTypeForm" scope="request"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="swabraModes" scope="request" class="jetbrains.buildServer.swabra.Modes"/>

<c:set var="displaySwabraSettings"
       value="${propertiesBean.properties['swabra.mode'] == 'swabra.before.build' ? true : false}"/>

<c:set var="displayNoModeSelectedNote"
       value="${empty propertiesBean.properties['swabra.mode'] ? true : false}"/>

<c:set var="displayBeforeBuildNote"
       value="${propertiesBean.properties['swabra.mode'] == 'swabra.before.build' ? true : false}"/>

<c:set var="displayAfterBuildNote"
       value="${propertiesBean.properties['swabra.mode'] == 'swabra.after.build' ? true : false}"/>

<l:settingsGroup title="Swabra">

    <tr class="noBorder" id="swabra.mode.container">
        <th><label for="swabra.mode">Perform build garbage cleanup:</label></th>
        <td>
            <c:set var="onchange">
                var selectedValue = this.options[this.selectedIndex].value;
                if (selectedValue == 'swabra.before.build') {
                BS.Util.show($('swabra.verbose.container'));

                BS.Util.hide($('swabra.mode.note'));
                BS.Util.show($('swabra.before.build.mode.note'));
                BS.Util.hide($('swabra.after.build.mode.note'));
                } else {
                if (selectedValue == 'swabra.after.build') {
                BS.Util.hide($('swabra.verbose.container'));

                BS.Util.hide($('swabra.mode.note'));
                BS.Util.hide($('swabra.before.build.mode.note'));
                BS.Util.show($('swabra.after.build.mode.note'));
                } else {
                BS.Util.hide($('swabra.verbose.container'));

                BS.Util.show($('swabra.mode.note'));
                BS.Util.hide($('swabra.before.build.mode.note'));
                BS.Util.hide($('swabra.after.build.mode.note'));
                }
                }
                $('swabra.verbose.container').disabled = (selectedValue != 'swabra.before.build');
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
      <span class="smallNote" id="swabra.mode.note" style="${displayNoModeSelectedNote ? '' : 'display: none;'}">
        Choose build garbage cleanup mode.
      </span>
    <span class="smallNote" id="swabra.before.build.mode.note"
          style="${displayBeforeBuildNote ? '' : 'display: none;'}">
        Previous build files cleanup will be performed at build start. You only need to use this mode
        if files are required between builds.
    </span>
    <span class="smallNote" id="swabra.after.build.mode.note" style="${displayAfterBuildNote ? '' : 'display: none;'}">
        Build files cleanup will be performed after the build. Between builds there will be clean copy in the checkout directory.
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