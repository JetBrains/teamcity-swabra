<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ page import="jetbrains.buildServer.swabra.serverHealth.SwabraFrequentCleanCheckoutReport" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>

<c:set var="groups" value="<%=healthStatusItem.getAdditionalData().get(SwabraFrequentCleanCheckoutReport.SWABRA_CLASHING_BUILD_TYPES)%>"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>

<c:if test="<%=(showMode == HealthStatusItemDisplayMode.GLOBAL)%>">
  <div>
    Build configurations have the same checkout directory but different Swabra settings:
  </div>
  <div>
    <c:forEach items="${groups}" var="group">
      <ul>
        <c:forEach items="${group.buildTypes}" var="bt">
          <c:choose>
            <c:when test="${afn:permissionGrantedForBuildType(bt, 'VIEW_PROJECT')}">
              <li>
                <admin:editBuildTypeLink buildTypeId="${bt.externalId}" step="runType" cameFromUrl="${pageUrl}"><c:out value="${bt.fullName}"/></admin:editBuildTypeLink>
                <i>(<c:if test="${group.settings.featurePresent}"
                      ><c:if test="${group.settings.cleanupEnabled}">cleanup enabled, ${group.settings.strict ? "strict" : "non-strict"}</c:if
                      ><c:if test="${not group.settings.cleanupEnabled}">cleanup disabled</c:if
                      ></c:if
                    ><c:if test="${not group.settings.featurePresent}">no build feature</c:if>)</i>
              </li>
            </c:when>
            <c:otherwise>
              <li><em>Inaccessible build configuration</em></li>
            </c:otherwise>
          </c:choose>
        </c:forEach>
      </ul>
    </c:forEach>
  </div>
</c:if>