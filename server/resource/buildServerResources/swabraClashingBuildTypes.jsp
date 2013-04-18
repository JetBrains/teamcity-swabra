<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>

<c:set var="differentSettingsBuildTypes" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>

<c:if test="<%=(showMode == HealthStatusItemDisplayMode.GLOBAL)%>">
  <div>
    Clean checkout can frequently happen in the following build configurations because they have the same checkout directory but different Build files cleaner (Swabra) settings <bs:help file="Build+Files+Cleaner+(Swabra)"/>
  </div>
  <div>
    <ul>
      <c:forEach items="${differentSettingsBuildTypes}" var="bt">
        <c:choose>
          <c:when test="${afn:permissionGrantedForBuildType(bt, 'VIEW_PROJECT')}">
            <li><admin:editBuildTypeLink buildTypeId="${bt.externalId}" step="vcsRoots" cameFromUrl="${pageUrl}"><c:out value="${bt.fullName}"/></admin:editBuildTypeLink></li>
          </c:when>
          <c:otherwise>
            <li><em>Inaccessible build configuration</em></li>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </ul>
  </div>
</c:if>