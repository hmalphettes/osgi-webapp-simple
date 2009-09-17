<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="x" uri="http://java.sun.com/jsp/jstl/xml" %>

<html>
<head>
  <title>JSTL: Importing XML Document</title>
</head>
<body bgcolor="#FFFFCC">
<h3>Transforming stocks.xml into HTML using stocks.xsl</h3>

<c:import url="http://www.javacourses.com/stocks.xml" var="xmldocument"/>
<c:import url="./stocks.xsl" var="xslt"/>
<x:transform xml="${xmldocument}" xslt="${xslt}"/>

</body>
</html>
