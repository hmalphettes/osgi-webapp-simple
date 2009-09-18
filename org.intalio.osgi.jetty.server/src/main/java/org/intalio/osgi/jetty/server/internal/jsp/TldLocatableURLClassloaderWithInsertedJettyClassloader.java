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

/**
 * Add a classloader to the TldLocatableURLClassloader.
 * Hopefuly not necessary: still experimenting.
 * @see TldLocatableURLClassloader 
 * 
 * @author hmalphettes
 */
public class TldLocatableURLClassloaderWithInsertedJettyClassloader extends TldLocatableURLClassloader {

	private ClassLoader _internalClassLoader;
	
	public TldLocatableURLClassloaderWithInsertedJettyClassloader(
			ClassLoader osgiClassLoader, ClassLoader internalClassLoader,
			URL[] jarsWithTldsInside) {
		super(osgiClassLoader, jarsWithTldsInside);
		_internalClassLoader = internalClassLoader;
	}
	
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			return super.findClass(name);
		} catch (ClassNotFoundException cne) {
			if (_internalClassLoader != null) {
				return _internalClassLoader.loadClass(name);
			} else {
				throw cne;
			}
		}
	}
}
