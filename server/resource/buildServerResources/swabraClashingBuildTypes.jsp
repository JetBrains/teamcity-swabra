<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>

<c:set var="groups" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>

<div>
  Build configurations have the same checkout directory but different Swabra settings:
</div>
<div>
  <c:forEach items="${groups}" var="group">
    <c:if test="<%=(showMode == HealthStatusItemDisplayMode.GLOBAL)%>"><%@ include file="clashingGroup.jspf"%></c:if>
    <c:if test="<%=(showMode == HealthStatusItemDisplayMode.IN_PLACE)%>">
      <c:forEach items="${group.buildTypes}" var="bt">
        <c:if test="${bt.id eq buildTypeId}"><c:set var="insideClashingGroup" value="${true}"/></c:if>
      </c:forEach>
      <c:if test="${insideClashingGroup}"><%@ include file="clashingGroup.jspf"%></c:if>
    </c:if>
  </c:forEach>
</div>