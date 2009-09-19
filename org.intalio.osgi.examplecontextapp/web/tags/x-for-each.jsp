<%@ page contentType="text/html" %>  
<%@ taglib  prefix="x" uri="http://java.sun.com/jsp/jstl/xml" %>
<html>

<body>  
 <x:parse var="doc"><books>           
    <book>
             <title>cobol</title>
             <author>roy</author>
    </book>
    <book>
             <title>java</title>
             <author>herbert</author>
    </book>
    <book>
             <title>c++</title>
             <author>robert</author>
    </book>
    <book>
             <title>coldfusion</title>
             <author>allaire</author>
    </book>
    <book>
             <title>xml unleashed</title>
             <author>morrison</author>
    </book>
    <book>
             <title>jrun</title>
             <author>allaire</author>
    </book>
</books>
 </x:parse>

 -----------------------------------------------<br/>

 <x:forEach     var="n"   
                     select="$doc/books/book">
 <x:out     select="$n/title"  />
  <br>  
 <x:out     select="$n/author"  />  
  <br>

 ==========
 <br>  
 </x:forEach>
</body>
</html>