<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<c:set var="cameFromUrl" value="${showMode eq inplaceMode ? pageUrl : healthStatusReportUrl}"/>

<c:set var="groups" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<style type="text/css">
  .groups ul {
    margin-top: 0.2em;
  }
  .groups {
    margin-top: 1em;
  }
</style>

<div class="swabraReportDescription">
  The following build configurations share checkout directory but have different Swabra settings. Make sure such build configurations have identical Swabra settings:<br/>
</div>
<div class="groups">
  <c:forEach items="${groups}" var="group" varStatus="groupPos">
    <div><em><c:if test="${group.settings.featurePresent}"
          ><c:if test="${group.settings.cleanupEnabled}">Swabra cleanup enabled, "Force clean checkout if cannot restore clean directory state" option ${group.settings.strict ? "enabled" : "disabled"}</c:if
          ><c:if test="${not group.settings.cleanupEnabled}">Swabra cleanup disabled</c:if
          ></c:if
        ><c:if test="${not group.settings.featurePresent}">No Swabra build feature enabled</c:if>:</em></div>
    <ul id="groupBuildTypes_${groupPos.index}">
      <c:set var="num" value="0"/>
      <c:forEach items="${group.buildTypes}" var="bt">
        <c:if test="${afn:permissionGrantedForBuildType(bt, 'VIEW_PROJECT')}">
          <c:set var="num" value="${num + 1}"/>
          <li class="${num > 3 ? 'hidden' : ''}">
            <admin:editBuildTypeLink buildTypeId="${bt.externalId}" step="runType" cameFromUrl="${cameFromUrl}"><c:out value="${bt.fullName}"/></admin:editBuildTypeLink>
          </li>
        </c:if>
      </c:forEach>
      <c:if test="${num > 3}"><a href="#" onclick="$j('#groupBuildTypes_${groupPos.index} li.hidden').toggleClass('hidden'); $j(this).hide(); return false;">view all (${num}) &raquo;</a></c:if>
    </ul>
  </c:forEach>
</div>