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

import java.util.Dictionary;

import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

/**
 * Support bundles that declare the webapp directly through headers in their manifest.
 * <p>
 * Those headers will define a new WebApplication:
 * <ul><li>Jetty-WarContextPath</li>
 *    <li>Jetty-WarFolderPath</li>
 *    <li>Jetty-ClassInBundle (optional if not found will default on the
 *    					   Bundle-Activator if there is one.)</li></ul>
 * </p>
 * <p>
 * Those headers will define a new app started via a jetty-context:
 * <ul><li>Jetty-ContextFilePath</li>
 *     <li>Jetty-ClassInBundle (optional if not found will default on
 *     						the Bundle-Activator if there is one.)</li></ul>
 * </p>
 * And generate a jetty WebAppContext or another ContextHandler then registers it
 * as service. Kind of simpler than declarative services and their xml files.
 * Also avoid having the contributing bundle depend on jetty's package for WebApp.
 * 
 * @author hmalphettes
 */
public class JettyContextHandlerExtender implements BundleListener {

	/**
	 * Receives notification that a bundle has had a lifecycle change.
	 * 
	 * @param event The <code>BundleEvent</code>.
	 */
	public void bundleChanged(BundleEvent event) {
		switch (event.getType()) {
		case BundleEvent.STARTED:
			register(event.getBundle());
			break;
		case BundleEvent.STOPPING:
			unregister(event.getBundle());
			break;
		}
	}
	
	/**
	 * 
	 */
	public void init(BundleContext context) {
		Bundle bundles[] = context.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			if ((bundles[i].getState() & (Bundle.STARTING | Bundle.ACTIVE)) != 0) {
				register(bundles[i]);
			}
		}
	}
	
	private void register(Bundle bundle) {
		Dictionary<?, ?> dic = bundle.getHeaders();
		String warFolderRelativePath = (String)dic.get("Jetty-WarFolderPath");
		if (warFolderRelativePath != null) {
			String contextPath = (String)dic.get("Jetty-WarContextPath");
			if (contextPath == null || !contextPath.startsWith("/")) {
				throw new IllegalArgumentException();
			}
			String nameOfClassInBundle = (String)dic.get("Jetty-ClassInBundle");
			//create the corresponding service and publish it in the context of
			//the contributor bundle.
			try {
				JettyBootstrapActivator.registerWebapplication(
						bundle, warFolderRelativePath, contextPath,
						nameOfClassInBundle);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String contextFileRelativePath = (String)dic.get("Jetty-ContextFilePath");
			if (contextFileRelativePath == null) {
				//nothing to register here.
				return;
			}
			String nameOfClassInBundle = (String)dic.get("Jetty-ClassInBundle");
			try {
				JettyBootstrapActivator.registerContext(
						bundle, contextFileRelativePath,
						nameOfClassInBundle);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void unregister(Bundle bundle) {
		//todo.
	}
	
}
