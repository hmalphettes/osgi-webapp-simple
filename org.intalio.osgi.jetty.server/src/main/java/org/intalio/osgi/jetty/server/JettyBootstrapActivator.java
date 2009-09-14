/******************************************************************************
* Copyright (c) 2006, Intalio Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
* 
* Contributors:
*     Intalio Inc. - initial API and implementation
*******************************************************************************/
package org.intalio.osgi.jetty.server;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Experiment: bootstrap jetty's complete distrib from an OSGi bundle.
 * 
 * @author hmalphettes
 * @author Intalio Inc
 */
public class JettyBootstrapActivator implements BundleActivator {

	private static JettyBootstrapActivator INSTANCE = null;
	
	public static JettyBootstrapActivator getInstance() {
		return INSTANCE;
	}
	
	private File _installLocation;
	private Server _server;

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		System.err.println("Activating" + this.getClass().getName());
		
		INSTANCE = this;
		_installLocation = getBundleInstallLocation(context.getBundle());
		
		String jettyHome = System.getProperty("jetty.home");
		if (jettyHome == null || jettyHome.length() == 0) {
			System.setProperty("jetty.home", _installLocation.getAbsolutePath() + "/jettyhome");
		}
		String jettyLogs = System.getProperty("jetty.logs");
		if (jettyLogs == null || jettyLogs.length() == 0) {
			System.setProperty("jetty.logs", System.getProperty("jetty.home") + "/logs");
		}
		ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
		try {
			_server = new Server();
			XmlConfiguration config = new XmlConfiguration(new FileInputStream(
					System.getProperty("jetty.home") + "/etc/jetty.xml"));
			Thread.currentThread().setContextClassLoader(JettyBootstrapActivator.class.getClassLoader());
			config.configure(_server);
			
			//check that there is a handler able to support webapps with this config:
			ContextHandlerCollection ctxtHandler = (ContextHandlerCollection)_server
					.getChildHandlerByClass(ContextHandlerCollection.class);
			if (ctxtHandler == null) {
				System.err.println("Warning: could not find a ContextHandlerCollection:" +
						" it won't be possible to register web-applications.");
			}
			
			_server.start();
//					_server.join();
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			Thread.currentThread().setContextClassLoader(contextCl);
		}
		
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		_server.stop();
		INSTANCE = null;
	}
	
	
	//hack to locate the file-system directly from the bundle.
	//support equinox, felix and nuxeo's osgi implementations.
	//not tested on nuxeo and felix just yet.
	//The url nuxeo and felix return is created directly from the File so it should work.
	private static Field BUNDLE_ENTRY_FIELD = null;
	private static Field FILE_FIELD = null;
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
	 * Deploy a new web application on the jetty server.
	 * 
	 * @param context The current bundle context
	 * @param webappFolderPath The path to the root of the webapp.
	 * Must be a path relative to bundle; either an absolute path.
	 * @param contextPath The context path. Must start with "/"
	 * @param classInBundle A class that belongs to the current bundle
	 * to inherit from the osgi classloader. Null to not have access to the
	 * OSGI classloader.
	 * @throws Exception
	 */
	public void registerWebapplication(Bundle bundle, String webappFolderPath,
			String contextPath, Class<?> classInBundle) throws Exception {
		File bundleInstall = getBundleInstallLocation(bundle);
		File webapp = webappFolderPath != null && webappFolderPath.length() != 0
			? new File(bundleInstall, webappFolderPath) : bundleInstall;
		if (!webapp.exists()) {
			throw new IllegalArgumentException("Unable to locate " + contextPath
					+ " inside ");
		}
		registerWebapplication(bundle, webapp, contextPath, classInBundle);
	}

	/**
	 * 
	 * @param webapp
	 * @param contextPath
	 * @param classInBundle
	 * @throws Exception
	 */
	public void registerWebapplication(Bundle contributor, File webapp, String contextPath, Class<?> classInBundle) throws Exception {
		WebAppContext context = new WebAppContext(webapp.getAbsolutePath(), contextPath);
		context.setDefaultsDescriptor(System.getProperty("jetty.home") + "/etc/webdefault.xml");
		
		WebXmlConfiguration webXml = new WebXmlConfiguration();
		webXml.configure(context);
		
		JettyWebXmlConfiguration jettyXml = new JettyWebXmlConfiguration();
		jettyXml.configure(context);
		
		//ok now register this webapp. we checked when we started jetty that there
		//was at least one such handler for webapps.
		ContextHandlerCollection ctxtHandler = (ContextHandlerCollection)_server
				.getChildHandlerByClass(ContextHandlerCollection.class);
		ctxtHandler.addHandler(context);
		
        //[Hugues] if we want the webapp to be able to load classes inside osgi
        //we must get a hold of the bundle's classloader.
        //I have not found a way to do this directly from the bundle object unfortunately.
        //As a workaround, we require the developer to declare the class name of
        //an object that is defined inside the bundle.
        //TODO: find a way to get the bundle's classloader directly from the org.osgi.framework.Bundle object (?)
        String bundleClassName = (String) contributor
        	.getHeaders().get("Webapp-InternalClassName");
        if (bundleClassName == null) {
        	bundleClassName = (String) contributor
        		.getHeaders().get("Bundle-Activator");
        }
        if (bundleClassName == null) {
        	//parse the web.xml and look for a class name there ?
        }
        if (bundleClassName != null) {
//this solution does not insert all the jetty related classes in the webapp's classloader:
//    		WebAppClassLoader cl = new WebAppClassLoader(classInBundle.getClassLoader(), context);
//    		context.setClassLoader(cl);

        	//Make all of the jetty's classes available to the webapplication classloader
        	//also add the contributing bundle's classloader to give access to osgi to
        	//the contributed webapp.
            ClassLoader osgiCl = contributor.loadClass(bundleClassName).getClassLoader();
		    ClassLoader composite = new TwinClassLoaders(
		    		JettyBootstrapActivator.class.getClassLoader(), osgiCl);
		    WebAppClassLoader wcl = new WebAppClassLoader(composite, context);
		    context.setClassLoader(wcl);
        } else {
        	//Make all of the jetty's classes available to the webapplication classloader
        	WebAppClassLoader wcl = new WebAppClassLoader(
        			JettyBootstrapActivator.class.getClassLoader(), context);
		    context.setClassLoader(wcl);
        }
		
		context.start();
		
	}
	
	public void unregister(String contextPath) {
		//TODO
	}

}
class TwinClassLoaders extends ClassLoader {
	private ClassLoader _cl2;
	public TwinClassLoaders(ClassLoader cl1, ClassLoader cl2) {
		super(cl1);
		_cl2 = cl2;
	}
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			return super.findClass(name);
		} catch (ClassNotFoundException cne) {
			if (_cl2 != null) {
				return _cl2.loadClass(name);
			} else {
				throw cne;
			}
		}
	}

}

