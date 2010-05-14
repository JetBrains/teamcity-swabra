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
  <c:when test="${not empty propertiesBean.properties['swabra.enabled']}">
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
      Paths to ignore: <props:displayValue name="swabra.ignored"
                                           emptyValue="none specified"/>
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

<c:set var="selected"
       value="${propertiesBean.properties['swabra.processes']}"/>

<div class="parameter">
  Locking processes:
  <c:choose>
    <c:when test="${selected == 'report'}">
      <strong>report</strong>
    </c:when>
    <c:when test="${selected == 'kill'}">
      <strong>kill</strong>
    </c:when>
    <c:otherwise>
      <strong>not detect</strong>
    </c:otherwise>
  </c:choose>
</div>
