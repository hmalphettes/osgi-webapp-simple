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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.intalio.osgi.jetty.server.internal.jsp.TldConfigurationHelper;
import org.intalio.osgi.jetty.server.internal.webapp.WebappRegistrationHelper;
import org.intalio.osgi.jetty.server.utils.FileLocatorHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

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
 * made all imports to ant, xalan and sun packages optional.</li>
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
	private WebappRegistrationHelper _webappRegistrationHelper;

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
			_webappRegistrationHelper = new WebappRegistrationHelper(_server);
			XmlConfiguration config = new XmlConfiguration(new FileInputStream(
					System.getProperty("jetty.home") + "/etc/jetty.xml"));
			
			//passing this bundle's classloader as the context classlaoder
			//makes sure there is access to all the jetty's bundles
			Thread.currentThread().setContextClassLoader(JettyBootstrapActivator.class.getClassLoader());
			config.configure(_server);
			
			_webappRegistrationHelper.init();
			
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
		_webappRegistrationHelper.registerWebapplication(bundle, webappFolderPath, contextPath, classInBundle);
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
		_webappRegistrationHelper.registerWebapplication(contributor, webapp, contextPath, classInBundle);
	}
	
	/**
	 * 
	 * @param contributor The bundle that registers a new context
	 * @param contextRelativePath The path to the file insie the bundle that defines the context.
	 * @param classInBundle
	 * @throws Exception
	 */
	public void registerContext(Bundle contributor, String contextRelativePath,
			Class<?> classInBundle) throws Exception {
		File contextFile = FileLocatorHelper.getFileInBundle(contributor, contextRelativePath);
		this.registerContext(contributor, contextFile, classInBundle);
	}
	/**
	 * 
	 * @param contributor
	 * @param contextFile The context file.
	 * @param classInBundle
	 * @throws Exception
	 */
	public void registerContext(Bundle contributor, File contextFile,
			Class<?> classInBundle) throws Exception {
		_webappRegistrationHelper.registerContext(contributor, contextFile, classInBundle);
	}
	
	
	public void unregister(String contextPath) {
		_webappRegistrationHelper.unregister(contextPath);
	}
	
	

}