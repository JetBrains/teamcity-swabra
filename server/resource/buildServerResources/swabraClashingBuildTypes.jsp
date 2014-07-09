<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>

<c:set var="groups" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<style type="text/css">
  .groups ul {
    margin-top: 0.2em;
  }
</style>
<div>
  Frequent clean checkout is possible because of inconsistent Swabra settings in the following build configurations: <bs:help file="Build+Files+Cleaner+(Swabra)"/>
</div>
<div class="groups">
  <c:forEach items="${groups}" var="group" varStatus="groupPos">
    <div><em><c:if test="${group.settings.featurePresent}"
          ><c:if test="${group.settings.cleanupEnabled}">Cleanup enabled, ${group.settings.strict ? "strict" : "non-strict"}</c:if
          ><c:if test="${not group.settings.cleanupEnabled}">Cleanup disabled</c:if
          ></c:if
        ><c:if test="${not group.settings.featurePresent}">No build feature</c:if>:</em></div>
    <ul id="groupBuildTypes_${groupPos.index}">
      <c:set var="num" value="0"/>
      <c:forEach items="${group.buildTypes}" var="bt">
        <c:if test="${afn:permissionGrantedForBuildType(bt, 'VIEW_PROJECT')}">
          <c:set var="num" value="${num + 1}"/>
          <li class="${num > 3 ? 'hidden' : ''}">
            <admin:editBuildTypeLink buildTypeId="${bt.externalId}" step="runType" cameFromUrl="${healthStatusReportUrl}"><c:out value="${bt.fullName}"/></admin:editBuildTypeLink>
          </li>
        </c:if>
      </c:forEach>
      <c:if test="${num > 3}"><a href="#" onclick="$j('#groupBuildTypes_${groupPos.index} li.hidden').toggleClass('hidden'); $j(this).hide(); return false;">view all (${num}) &raquo;</a></c:if>
    </ul>
  </c:forEach>
</div>