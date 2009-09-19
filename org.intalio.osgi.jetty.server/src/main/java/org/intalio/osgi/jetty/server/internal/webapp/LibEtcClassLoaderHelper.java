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
package org.intalio.osgi.jetty.server.internal.webapp;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import org.eclipse.jetty.server.Server;

/**
 * Helper to create a URL class-loader with the jars inside ${jetty.home}/lib/etc
 * In an ideal world, every library is an OSGi bundle that does loads nicely.
 * To support standard jars or bundles that cannot be loaded in the current
 * OSGi environment, we support inserting the jars in the usual jetty/lib/etc
 * folders in the proper classpath for the webapps.
 * <p>
 * For example the test-jndi webapplication depends on derby, derbytools, atomikos
 * none of them are osgi bundles.
 * we can either re-package them or we can place them in the usual lib/etc.
 * <br/>In fact jasper's jsp libraries should maybe place in lib/etc too.
 * </p>
 * <p>
 * The drawback is that those libraries will not be available in the OSGi classloader.
 * Note that we could have setup those jars as embedded jars of the current bundle.
 * However, we would need to know in advance what are those jars which was not acceptable.
 * Also having those jars in a URLClassLoader seem to be required for some cases.
 * For example jaspers' TldLocationsCache (replaced by TldScanner for servlet-3.0).
 * <br/>
 * Also all the dependencies of those libraries must be resolvable directly
 * from the JettyBooStrapper bundle as it is set as the parent classloader.
 * For example: if atomikos is placed in lib/etc
 * it will work if and only if JettyBootStrapper import the necessary packages
 * from javax.naming*, javax.transaction*, javax.mail* etc
 * Most of the common cases of javax are added as optional import packages into
 * jetty bootstrapper plugin. When there are not covered: please make a request
 * or create a fragment or register a bundle with a buddy-policy onto the jetty bootstrapper..
 * </p>
 * <p>
 * Alternatives to placing jars in lib/etc
 * <ol>
 * <li>Bundle the jars in an osgi bundle. Have the webapp(s) that context depends on them
 * depend on that bundle. Things will go well for jetty.</li>
 * <li>Bundle those jars in an osgi bundle-fragment that targets the jetty-bootstrap bundle</li>
 * <li>Use equinox Buddy-Policy: register a buddy of the jetty bootstrapper bundle.
 * (least favorite: it will work only on equinox)</li>
 * </ol>
 * </p>
 * @author hmalphettes
 */
public class LibEtcClassLoaderHelper {

	/**
	 * @param server
	 * @return a url classloader with the jars of lib/etc. The parent classloader
	 * usuall is the JettyBootStrapper.
	 * @throws MalformedURLException 
	 */
	public static URLClassLoader createLibEtcClassLoaderHelper(File jettyHome, Server server,
			ClassLoader parentClassLoader)
	throws MalformedURLException {
		File libEtc = new File(jettyHome, "lib/etc");
		ArrayList<URL> urls = new ArrayList<URL>();
		for (File f : libEtc.listFiles()) {
			if (f.getName().endsWith(".jar")) {
				//cheap to tolerate folders so let's do it.
				URL url = f.toURI().toURL();
				if (f.isFile()) {//is this necessary anyways?
					url = new URL("jar:" + url.toString() + "!/");
				}
				urls.add(url);
			}
		}
		return new URLClassLoader(urls.toArray(new URL[urls.size()]),
				parentClassLoader);
	}
	
	
}
