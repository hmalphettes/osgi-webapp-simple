package org.intalio.osgi.jetty.boot;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogService;

public class Registry {
	
	private Set<Bundle> activated = new HashSet<Bundle>();
	WebapplicationService webapps = JettyBootActivator.getInstance();
	LogService  log;

	public void register(Bundle bundle) {
		synchronized (activated) {
			if (activated.contains(bundle))
				return;
		}
		try {
			String contextPath = (String) bundle.getHeaders().get("WebappContextPath");
			if (contextPath == null) {
				return;
			}
			String className = (String) bundle.getHeaders().get("WebappBundleClass");
			Class<?> bundleClass = null;
			if (className != null && className.length() != 0) {
				bundleClass = bundle.loadClass(className);
			}
			String webRootFolder = (String) bundle.getHeaders().get("WebappRelativeRootFolder");
			if (webRootFolder == null) {
				webRootFolder = "";
			}
			webapps.registerWebapplication(bundle.getBundleContext(),
					contextPath, webRootFolder, bundleClass);
		} catch (Throwable t) {
			log.log(LogService.LOG_ERROR,
					"[extender] Activating webapp from "
							+ bundle.getLocation(), t);
		}
		synchronized (activated) {
			activated.add(bundle);
		}
	}

	public void unregister(Bundle bundle) {
		synchronized (activated) {
			if (!activated.contains(bundle))
				return;
			activated.remove(bundle);
		}
		
	}

	void close() {
		for (Iterator<Bundle> i = activated.iterator(); i.hasNext();) {
			Bundle bundle = (Bundle) i.next();
			unregister(bundle);
		}
	}

}
