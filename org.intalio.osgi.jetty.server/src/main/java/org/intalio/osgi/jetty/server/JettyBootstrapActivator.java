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

import java.util.Properties;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.intalio.osgi.jetty.server.internal.webapp.JettyContextHandlerExtender;
import org.intalio.osgi.jetty.server.internal.webapp.JettyContextHandlerServiceTracker;
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
 * <li> jsp with tag-libs [ok]</li>
 * <li> test-jndi with atomikos and derby inside ${jetty.home}/lib/etc [ok]</li>
 * </ul>
 * @author hmalphettes
 * @author Intalio Inc
 */
public class JettyBootstrapActivator implements BundleActivator {

	private static JettyBootstrapActivator INSTANCE = null;
	
	public static JettyBootstrapActivator getInstance() {
		return INSTANCE;
	}
	
	private Server _server;
	
	/**
	 * Setup a new jetty Server, registers it as a service.
	 * Setup the Service tracker for the jetty ContextHandlers that are in
	 * charge of deploying the webapps.
	 * Setup the BundleListener that supports the extender pattern for the
	 * jetty ContextHandler.
	 * 
	 * @param context
	 */
	public void start(BundleContext context) throws Exception {
		System.err.println("Activating" + this.getClass().getName());
		INSTANCE = this;
		_server = new Server();
		//expose the server as a service. 
		context.registerService(_server.getClass().getName(), _server, new Properties());
		//the tracker in charge of the actual deployment
		//and that will configure and start the jetty server.
		JettyContextHandlerServiceTracker jettyContextHandlerTracker =
			new JettyContextHandlerServiceTracker(context, _server);
		
		//TODO: add a couple more checks on the properties?
		//kind of nice not to so we can debug what is missing easily.
		context.addServiceListener(jettyContextHandlerTracker,
				"(objectclass=" + ContextHandler.class.getName() + ")");
		
		//now ready to support the Extender pattern:
		JettyContextHandlerExtender jettyContexHandlerExtender = 
			new JettyContextHandlerExtender();
		context.addBundleListener(jettyContexHandlerExtender);
		
		jettyContexHandlerExtender.init(context);
		

	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		INSTANCE = null;
		_server.stop();
	}
	
	

	/**
	 * Helper method that creates a new org.jetty.webapp.WebAppContext
	 * and registers it as an OSGi service.
	 * The tracker {@link JettyContextHandlerServiceTracker} will do the actual deployment.
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
	public static void registerWebapplication(Bundle contributor, String webappFolderPath,
			String contextPath, String classInBundle) throws Exception {
		WebAppContext contextHandler = new WebAppContext();
		Properties dic = new Properties();
		dic.put("war", webappFolderPath);
		dic.put("contextPath", contextPath);
		dic.put("classInBundle", classInBundle);
		contributor.getBundleContext().registerService(
				ContextHandler.class.getName(),
				contextHandler, dic);
	}

	/**
	 * Helper method that creates a new skeleton of a ContextHandler.
	 * and registers it as an OSGi service.
	 * The tracker {@link JettyContextHandlerServiceTracker} will do the actual deployment.
	 * 
	 * @param contributor The bundle that registers a new context
	 * @param contextFilePath The path to the file inside the bundle that defines the context.
	 * @param classInBundle Name of a class that is in the bundle.
	 * @throws Exception
	 */
	public static void registerContext(Bundle contributor, String contextFilePath,
			String classInBundle) throws Exception {
		ContextHandler contextHandler = new ContextHandler();
		Properties dic = new Properties();
		dic.put("contextFilePath", contextFilePath);
		dic.put("classInBundle", classInBundle);
		contributor.getBundleContext().registerService(
				ContextHandler.class.getName(),
				contextHandler, dic);
	}
	
	
	public static void unregister(String contextPath) {
		//todo
	}
	
	

}