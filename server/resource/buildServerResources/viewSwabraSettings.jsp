<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<div class="parameter">
  Swabra:
  <c:choose>
    <c:when test="${empty propertiesBean.properties['swabra.mode']}">
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
      Swabra mode: <props:displayValue name="swabra.mode"
                                       emptyValue="none specified"/>
    </div>
  </c:when>
</c:choose>

<c:choose>
    <c:when test="${not empty propertiesBean.properties['swabra.mode']}">
      <div class="parameter">
        Strict mode:
        <c:choose>
          <c:when test="${propertiesBean.properties['swabra.strict']}">
            <strong>enabled</strong>
          </c:when>
          <c:otherwise>
            <strong>disabled</strong>
          </c:otherwise>
        </c:choose>
      </div>
    </c:when>
</c:choose>



<c:choose>
  <c:when test="${not empty propertiesBean.properties['swabra.locking.processes']}">
    <div class="parameter">
      Locking processes detection:
      <strong>enabled</strong>      
    </div>
    <div class="parameter">
      Handle.exe path:
      <c:choose>
        <c:when test="${empty propertiesBean.properties['swabra.process.analizer']}">
          <strong>none specified</strong>
        </c:when>
        <c:otherwise>
          <strong>${propertiesBean.properties['swabra.process.analizer']}</strong>
        </c:otherwise>
      </c:choose>
    </div>
  </c:when>
  <c:otherwise>
    <div class="parameter">
      Locking processes detection:
      <strong>disabled</strong>
    </div>
  </c:otherwise>
</c:choose>

<c:choose>
  <c:when test="${propertiesBean.properties['swabra.mode'] == 'swabra.before.build'}">
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
