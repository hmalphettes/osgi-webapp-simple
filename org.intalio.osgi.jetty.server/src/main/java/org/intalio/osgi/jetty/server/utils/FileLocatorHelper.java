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

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.osgi.framework.Bundle;

/**
 * From bundle to its location on the filesystem.
 * 
 * @author hmalphettes
 */
public class FileLocatorHelper {

	//hack to locate the file-system directly from the bundle.
	//support equinox, felix and nuxeo's osgi implementations.
	//not tested on nuxeo and felix just yet.
	//The url nuxeo and felix return is created directly from the File so it should work.
	private static Field BUNDLE_ENTRY_FIELD = null;
	private static Field FILE_FIELD = null;
	
	/**
	 * Works with equinox, felix, nuxeo and probably more.
	 * Not exactly in the spirit of OSGi but quite necessary to support
	 * self-contained webapps and other situations.
	 * <p>
	 * Currently only works with bundles that are not jar.
	 * </p>
	 * @param bundle The bundle
	 * @return Its installation location as a file.
	 * @throws Exception
	 */
	public static File getBundleInstallLocation(Bundle bundle) throws Exception {
		//String installedBundles = System.getProperty("osgi.bundles");
		//grab the MANIFEST.MF's url
		//and then do what it takes.
		URL url = bundle.getEntry("/META-INF/MANIFEST.MF");
//			System.err.println(url.toString() + " " + url.toURI() + " " + url.getProtocol());
		if (url.getProtocol().equals("file")) {
			//this is the case with Felix and maybe other OSGI frameworks
			//should make sure it is not a jar.
			return new File(url.toURI()).getParentFile().getParentFile();
		} else if (url.getProtocol().equals("bundleentry")) {
			//say hello to equinox who has its own protocol.
			//we use introspection like there is no tomorrow to get access to the File
			URLConnection con = url.openConnection();
			if (BUNDLE_ENTRY_FIELD == null) {
				BUNDLE_ENTRY_FIELD = con.getClass().getDeclaredField("bundleEntry");
				BUNDLE_ENTRY_FIELD.setAccessible(true);
			}
			Object bundleEntry = BUNDLE_ENTRY_FIELD.get(con);
			if (FILE_FIELD == null) {
				FILE_FIELD = bundleEntry.getClass().getDeclaredField("file");
				FILE_FIELD.setAccessible(true);
			}
			File f = (File)FILE_FIELD.get(bundleEntry);
			return f.getParentFile().getParentFile();
		}
		return null;
	}
	
	/**
	 * If the bundle is a jar, returns the jar.
	 * If the bundle is a folder, look inside it and search for jars that it returns.
	 * 
	 * Good enough for our purpose (TldLocationsCache when it scans for tld files
	 * inside jars alone.
	 * In fact we only support the second situation for development purpose where
	 * the bundle was imported in pde and the classes kept in a jar.
	 * 
	 * @param bundle
	 * @return The jar(s) file that is either the bundle itself, either the jars embedded inside it.
	 */
	public static File[] locateJarsInsideBundle(Bundle bundle) throws Exception {
		File jasperLocation = FileLocatorHelper.getBundleInstallLocation(bundle);
		if (jasperLocation.isDirectory()) {
			//try to find the jar files inside this folder
			ArrayList<File> urls = new ArrayList<File>();
			for (File f : jasperLocation.listFiles()) {
				if (f.getName().endsWith(".jar") && f.isFile()) {
					urls.add(f);
				} else if (f.isDirectory() && f.getName().equals("lib")) {
					for (File f2 : jasperLocation.listFiles()) {
						if (f2.getName().endsWith(".jar") && f2.isFile()) {
							urls.add(f2);
						}
					}
				}
			}
			return urls.toArray(new File[urls.size()]);
		} else {
			return new File[] {jasperLocation};
		}

	}
	
	

	
}
