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
package org.intalio.osgi.jetty.server;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;

import org.apache.jasper.compiler.TldLocationsCache;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.intalio.osgi.jetty.server.internal.jsp.TldConfigurationHelper;
import org.intalio.osgi.jetty.server.internal.jsp.TldLocatableURLClassloader;
import org.intalio.osgi.jetty.server.internal.jsp.TldLocatableURLClassloaderWithInsertedJettyClassloader;
import org.intalio.osgi.jetty.server.utils.FileLocatorHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Experiment: bootstrap jetty's complete distrib from an OSGi bundle.
 * Progress:
 * <ol>
 * <li> basic servlet [ok]</li>
 * <li> basic jetty.xml [ok]</li>
 * <li> basic jetty.xml and jetty-plus.xml [ok]</li>
 * <li> basic jsp [ok with modifications]
 *   <ul>
 *     <li>Needed to modify the headers of jdt.core-3.1.1 so that its dependency on 
 * eclipse.runtime, eclipse.resources and eclipse.text are optional.
 * Also we should depend on the latest jdt.core from eclipse-3.5 not from eclipse-3.1.1
 * although that will require actual changes to jasper as some internal APIs of
 * jdt.core have changed.</li>
 *     <li>Modifications to org.mortbay.jetty.jsp-2.1-glassfish:
 * made all imports to ant, xalan and sun pacakges optional.</li>
 *   </ul>
 * </li>
 * <li> jsp with tag-libs [untested]</li>
 * </ul>
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
		TldConfigurationHelper.fixupDtdResolution();
		
		INSTANCE = this;
		_installLocation = FileLocatorHelper.getBundleInstallLocation(context.getBundle());
		
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
			
			//passing this bundle's classloader as the context classlaoder
			//makes sure there is access to all the jetty's bundles
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
		File bundleInstall = FileLocatorHelper.getBundleInstallLocation(bundle);
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
	public void registerWebapplication(Bundle contributor, File webapp,
			String contextPath, Class<?> classInBundle) throws Exception {
		
		ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
		try {
			//make sure we provide access to all the jetty bundles by going through this bundle.
			Thread.currentThread().setContextClassLoader(JettyBootstrapActivator.class.getClassLoader());
			
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
	            ClassLoader composite = //new TwinClassLoaders(
	            	new TldLocatableURLClassloaderWithInsertedJettyClassloader(
			    		JettyBootstrapActivator.class.getClassLoader(), osgiCl,
			    		getBundlesWithTlds());
			    WebAppClassLoader wcl = new WebAppClassLoader(composite, context);
			    //addJarsWithTlds(wcl);
			    context.setClassLoader(wcl);
	        } else {
	        	//Make all of the jetty's classes available to the webapplication classloader
	        	WebAppClassLoader wcl = new WebAppClassLoader(
	        			new TldLocatableURLClassloader(
	    			    		JettyBootstrapActivator.class.getClassLoader(),
	    			    		getBundlesWithTlds()), context);
	        	//addJarsWithTlds(wcl);
			    context.setClassLoader(wcl);
	        }
			
			context.start();
		
		} finally {
			Thread.currentThread().setContextClassLoader(contextCl);
		}
		
	}
	
	public void unregister(String contextPath) {
		//TODO
	}
	
	/**
	 * The jasper TldScanner expects a URLClassloader to parse a jar for the
	 * /META-INF/*.tld it may contain.
	 * We place the bundles that we know contain such tag-libraries.
	 * Please note that it will work if and only if the bundle is a jar (!)
	 * Currently we just hardcode the bundle that contains the jstl implemenation.
	 * 
	 * A workaround when the tld cannot be parsed with this method is to copy and paste
	 * it inside the WEB-INF of the webapplication where it is used.
	 * 
	 * @return
	 * @throws Exception
	 */
	private URL[] getBundlesWithTlds() throws Exception {
		Bundle jasperBundler = FrameworkUtil.getBundle(TldLocationsCache.class);
		File jasperLocation = FileLocatorHelper.getBundleInstallLocation(jasperBundler);
		if (jasperLocation.isDirectory()) {
			//try to find the jar files inside this folder
			ArrayList<URL> urls = new ArrayList<URL>();
			for (File f : jasperLocation.listFiles()) {
				if (f.getName().endsWith(".jar") && f.isFile()) {
					urls.add(f.toURI().toURL());
				} else if (f.isDirectory() && f.getName().equals("lib")) {
					for (File f2 : jasperLocation.listFiles()) {
						if (f2.getName().endsWith(".jar") && f2.isFile()) {
							urls.add(f2.toURI().toURL());
						}
					}
				}
			}
			return urls.toArray(new URL[urls.size()]);
		} else {
			return new URL[] {jasperLocation.toURI().toURL()};
		}
	}
	

}