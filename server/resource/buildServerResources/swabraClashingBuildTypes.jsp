<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>

<c:set var="groups" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>

<div>
  Build configurations run on the same agent in the same checkout directory but have different Swabra settings:
</div>
<div>
  <c:forEach items="${groups}" var="group">
    <%@ include file="clashingGroup.jspf"%>
  </c:forEach>
</div>