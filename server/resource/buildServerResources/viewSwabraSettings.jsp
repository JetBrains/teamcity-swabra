 <%--Copyright 2000-2010 JetBrains s.r.o.--%>

 <%--Licensed under the Apache License, Version 2.0 (the "License");--%>
 <%--you may not use this file except in compliance with the License.--%>
 <%--You may obtain a copy of the License at--%>

 <%--http://www.apache.org/licenses/LICENSE-2.0--%>

 <%--Unless required by applicable law or agreed to in writing, software--%>
 <%--distributed under the License is distributed on an "AS IS" BASIS,--%>
 <%--WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.--%>
 <%--See the License for the specific language governing permissions and--%>
 <%--limitations under the License.--%>

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

<c:set var="processesSelected"
       value="${propertiesBean.properties['swabra.processes']}"/>

<div class="parameter">
  Locking processes:
  <c:choose>
    <c:when test="${processesSelected == 'report'}">
      <strong>report</strong>
    </c:when>
    <c:when test="${processesSelected == 'kill'}">
      <strong>kill</strong>
    </c:when>
    <c:otherwise>
      <strong>do not detect</strong>
    </c:otherwise>
  </c:choose>
</div>
