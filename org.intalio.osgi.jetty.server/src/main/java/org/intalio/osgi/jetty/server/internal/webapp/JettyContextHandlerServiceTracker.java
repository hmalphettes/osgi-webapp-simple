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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * When a {@link ContextHandler} service is activated we look into it and if
 * the corresponding webapp is actually not configured then we go and register it.
 * <p>
 * The idea is to always go through this class when we deploy a new webapp
 * on jetty.
 * </p>
 * 
 * @author hmalphettes
 */
public class JettyContextHandlerServiceTracker implements ServiceListener {
	
	private final WebappRegistrationHelper _helper;
	
	/**
	 * @param context
	 * @param server
	 */
	public JettyContextHandlerServiceTracker(BundleContext context, Server server)
	throws Exception {
		_helper = new WebappRegistrationHelper(server);
		_helper.setup(context);
	}
	
	
	/**
	 * Receives notification that a service has had a lifecycle change.
	 * 
	 * @param ev The <code>ServiceEvent</code> object.
	 */
	public void serviceChanged(ServiceEvent ev) {
		ServiceReference sr = ev.getServiceReference();
		switch(ev.getType()) {
			case ServiceEvent.REGISTERED: {
				Bundle contributor = sr.getBundle();
				BundleContext context = FrameworkUtil
					.getBundle(JettyBootstrapActivator.class).getBundleContext();
				ContextHandler contextHandler = (ContextHandler) context.getService(sr);
				if (contextHandler.getServer() != null) {
					//is configured elsewhere.
					return;
				}
				if (contextHandler instanceof WebAppContext) {
					WebAppContext webapp = (WebAppContext)contextHandler;
					String contextPath = (String)sr.getProperty("contextPath");
					if (contextPath == null) {
						contextPath = webapp.getContextPath();
					}
					String war = (String)sr.getProperty("war");
					String nameOfClassInBundle = (String)sr.getProperty("classInBundle");
					if (nameOfClassInBundle == null) {
						nameOfClassInBundle = (String)contributor.getHeaders().get("Bundle-Activator");
						if (nameOfClassInBundle == null) {
//							BundleClassLoaderHelper.
						}
					}
					try {
						_helper.registerWebapplication(contributor, war, contextPath,
								contributor.loadClass(nameOfClassInBundle));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					//consider this just an empty skeleton:
					String contextFilePath = (String)sr.getProperty("contextFilePath");
					if (contextFilePath == null) {
						throw new IllegalArgumentException("the property contextFilePath is required");
					}
					String nameOfClassInBundle = (String)sr.getProperty("classInBundle");
					if (nameOfClassInBundle == null) {
						nameOfClassInBundle = (String)contributor.getHeaders().get("Bundle-Activator");
						if (nameOfClassInBundle == null) {
//							BundleClassLoaderHelper.
						}
					}
					try {
						_helper.registerContext(
								contributor, contextFilePath,
								contributor.loadClass(nameOfClassInBundle));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			break;
			case ServiceEvent.UNREGISTERING: {
				//JettyBootstrapActivator.getInstance().unregisterContext(contributor, contextFile, classInBundle);
				//TODO
			}
			break;
		}
	}
	
}
