<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<div class="parameter">
  Swabra:
  <c:choose>
    <c:when test="${empty propertiesBean.properties['swabra.enabled']}">
      <strong>disabled</strong>
    </c:when>
    <c:otherwise>
      <strong>enabled</strong>
    </c:otherwise>
  </c:choose>
</div>

<c:choose>
  <c:when test="${not empty propertiesBean.properties['swabra.mode']}">
    <div class="parameter">
      Kill processes:
      <c:choose>
        <c:when test="${propertiesBean.properties['swabra.kill']}">
          <strong>enabled</strong>
        </c:when>
        <c:otherwise>
          <strong>disabled</strong>
        </c:otherwise>
      </c:choose>
    </div>
    <div class="parameter">
      Ensure clean checkout directory:
      <c:choose>
        <c:when test="${propertiesBean.properties['swabra.strict']}">
          <strong>enabled</strong>
        </c:when>
        <c:otherwise>
          <strong>disabled</strong>
        </c:otherwise>
      </c:choose>
    </div>
    <div class="parameter">
      Verbose output:
      <c:choose>
        <c:when test="${propertiesBean.properties['swabra.verbose']}">
          <strong>enabled</strong>
        </c:when>
        <c:otherwise>
          <strong>disabled</strong>
        </c:otherwise>
      </c:choose>
    </div>
  </c:when>
</c:choose>

<div class="parameter">
  Locking processes detection:
  <c:choose>
    <c:when test="${not empty propertiesBean.properties['swabra.locking.processes']}">
      <strong>enabled</strong>
    </c:when>
    <c:otherwise>
      <strong>disabled</strong>
    </c:otherwise>
  </c:choose>
</div>
