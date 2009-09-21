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
package org.intalio.osgi.jetty.server.utils;

import org.osgi.framework.Bundle;

/**
 * Is there a clean OSGi way to go from the Bundle object to the classloader of the Bundle ?
 * You can certainly take a class inside the bundle and get the bundle's classloader
 * that way.
 * But from the Bundle would be nice.
 * This is dirty and only works on equinox!
 * 
 * @author hmalphettes
 */
public class BundleClassLoaderHelper {

	/**
	 * Assuming the bundle is started.
	 * @param bundle
	 * @return
	 */
	public ClassLoader getClassLoader(Bundle bundle) {
		//TODO with introspection ?
//		if (bundle instanceof org.eclipse.osgi.framework.internal.core.AbstractBundle) {
//			org.eclipse.osgi.internal.loader.BundleLoader bLoader =
//					bundle.getBundleLoader();
//			return bLoader.createClassLoader();
//		}
		String bundleActivator = (String)bundle.getHeaders().get("Bundle-Activator");
		if (bundleActivator != null) {
			try {
				return bundle.loadClass(bundleActivator).getClassLoader();
			} catch (ClassNotFoundException e) {
				//should not happen as we are called if the bundle is started anyways.
				e.printStackTrace();
			}
		}
		return null;
	}
	
}
