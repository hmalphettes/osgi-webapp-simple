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

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.jasper.compiler.TldLocationsCache;

/**
 * Tricky hacky url classloader.
 * In fact we don't want a real URLClassLoader: we want OSGi's classloader
 * to do what they do.
 * But to let {@link TldLocationsCache} find the core tlds inside the jars
 * we must be a URLClassLoader that returns an array of jars where tlds are stored
 * when the method getURLs is called.
 * 
 * @author hmalphettes
 */
public class TldLocatableURLClassloader extends URLClassLoader {

	private URL[] _jarsWithTldsInside;
	
	public TldLocatableURLClassloader(ClassLoader osgiClassLoader,
			URL[] jarsWithTldsInside) {
		super(new URL[] {}, osgiClassLoader);
		_jarsWithTldsInside = jarsWithTldsInside;
	}
	
	/**
	 * @return the jars that contains tlds so that TldLocationsCache or TldScanner
	 * can find them.
	 */
	@Override
	public URL[] getURLs() {
		return _jarsWithTldsInside;
	}
}
