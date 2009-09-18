/******************************************************************************
* Copyright (c) 2009, Intalio Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
* 
* Contributors:
*     Intalio Inc. - initial API and implementation
*******************************************************************************/
package org.intalio.osgi.jetty.server.internal.jsp;

import java.io.InputStream;

import javax.servlet.jsp.JspContext;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.xmlparser.ParserUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Fix various shortcomings with the way jasper parses the tld files.
 * 
 * @author hmalphettes
 */
public class TldConfigurationHelper {
	
	/**
	 * Jasper resolves the dtd when it parses a taglib descriptor.
	 * It uses this code to do that:
	 * ParserUtils.getClass().getResourceAsStream(resourcePath);
	 * where resourcePath is for example: /javax/servlet/jsp/resources/web-jsptaglibrary_1_2.dtd
	 * Unfortunately, the dtd file is not in the exact same classloader
	 * as ParserUtils class and the dtds are packaged in 2 separate bundles.
	 * OSGi does not look in the dependencies' classloader when a resource is searched.
	 * <p>
	 * The workaround consists of setting the entity resolver.
	 * That is a patch added to the version of glassfish-jasper-jetty.
	 * IT is also present in the latest version of glassfish jasper.
	 * Could not use introspection to set new value on a static friendly field :(
	 * </p>
	 */
	public static void fixupDtdResolution() {
		try {
//			ClassLoader cl = /*ParserUtils*/JspPage.class.getClassLoader();
//			URL url = cl.getResource("/javax/servlet/jsp/resources/web-jsptaglibrary_1_2.dtd");
//			Field entityResolver = ParserUtils.class.getDeclaredField("entityResolver");
//			entityResolver.setAccessible(true);
//			entityResolver.set(null, new MyLSResourceResolver());
			//
			ParserUtils.setEntityResolver(new MyFixedupEntityResolver());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Instead of using the ParserUtil's classloader, we use a class
     * that is indeed next to the resource for sure.
     */
	static class MyFixedupEntityResolver implements EntityResolver {
	    /**
	     * Same values than in ParserUtils...
	     */
	    static final String[] CACHED_DTD_PUBLIC_IDS = {
		Constants.TAGLIB_DTD_PUBLIC_ID_11,
		Constants.TAGLIB_DTD_PUBLIC_ID_12,
		Constants.WEBAPP_DTD_PUBLIC_ID_22,
		Constants.WEBAPP_DTD_PUBLIC_ID_23,
	    };

	    // START PWC 6386258
	    static final String[] CACHED_DTD_RESOURCE_PATHS = {
	        Constants.TAGLIB_DTD_RESOURCE_PATH_11,
	        Constants.TAGLIB_DTD_RESOURCE_PATH_12,
	        Constants.WEBAPP_DTD_RESOURCE_PATH_22,
	        Constants.WEBAPP_DTD_RESOURCE_PATH_23,
	    };

//	    static final String[] CACHED_SCHEMA_RESOURCE_PATHS = {
//	        Constants.TAGLIB_SCHEMA_RESOURCE_PATH_20,
//	        Constants.TAGLIB_SCHEMA_RESOURCE_PATH_21,
//	        Constants.WEBAPP_SCHEMA_RESOURCE_PATH_24,
//	        Constants.WEBAPP_SCHEMA_RESOURCE_PATH_25,
//	    };
	    public InputSource resolveEntity(String publicId, String systemId)
	        throws SAXException {
	        for (int i=0; i<CACHED_DTD_PUBLIC_IDS.length; i++) {
	            String cachedDtdPublicId = CACHED_DTD_PUBLIC_IDS[i];
	            if (cachedDtdPublicId.equals(publicId)) {
	                /* PWC 6386258
	                String resourcePath = Constants.CACHED_DTD_RESOURCE_PATHS[i];
	                */
	                // START PWC 6386258
	                String resourcePath = CACHED_DTD_RESOURCE_PATHS[i];
	                // END PWC 6386258
	                InputStream input = null;
//	                if (false /*ParserUtils.isDtdResourcePrefixFileUrl*/) {//we don't need this.
//	                    try {
//	                        File path = new File(new URI(resourcePath));
//	                        if (path.exists()) {
//	                            input = new FileInputStream(path);
//	                        }
//	                    } catch(Exception e) {
//	                        throw new SAXException(e);
//	                    }
//	                } else {
	        	        //instead of using the ParserUtil's classloader, we use a class
	        	        //that is indeed "__next__" to the resource for sure.
	                    input = JspContext.class.getResourceAsStream(resourcePath);
//	                }
	                if (input == null) {
	                	//if that failed try again with the original code:
	                	//although it is likely not changed.
	                	input = this.getClass().getResourceAsStream(resourcePath);
	                }
	                if (input == null) {
	                    throw new SAXException(
	                        Localizer.getMessage("jsp.error.internal.filenotfound",
	                                             resourcePath));
	                }
	                InputSource isrc = new InputSource(input);
	                return isrc;
	            }
	        }

//	        if (ParserUtils.log.isLoggable(Level.FINE)) {
//	            ParserUtils.log.fine("Resolve entity failed"  + publicId + " "
//	                                  + systemId );
//	        }
//
//	        ParserUtils.log.severe(
//	            Localizer.getMessage("jsp.error.parse.xml.invalidPublicId",
//	            publicId));

	        return null;
	    }
	}
	
}
