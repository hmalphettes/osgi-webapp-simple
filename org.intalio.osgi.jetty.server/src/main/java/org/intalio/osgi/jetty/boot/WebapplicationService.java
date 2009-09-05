package org.intalio.osgi.jetty.boot;

import org.osgi.framework.BundleContext;

/**
 * Similar to HttpService but for a Webapplication.
 * Not really setup as an OSGI service yet.
 * 
 * @author hmalphettes
 * @author Intalio Inc.
 */
public interface WebapplicationService {

	/**
	 * @param contextPath
	 * @param relativePathToWebappFolder Path realtive to the bundle where the root of the
	 * webapp folder is.
	 * @param osgiLoadedClass A class that belongs to the bundle
	 * if we want the osgi-classloader to be a parent of the webapp-classloader
	 */
	public void registerWebapplication(BundleContext context, String contextPath,
			String relativePathToWebappFolder, Class<?> osgiLoadedClass)
	 throws Exception;
	
	/**
	 * @param contextPah The context path
	 */
	public void unregister(String contextPah);
	
	
}
