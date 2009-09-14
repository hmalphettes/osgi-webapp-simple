package org.intalio.osgi.examplewebapp;

import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		 System.err.println("starting a webapp bundle");
		 try {
			 JettyBootstrapActivator.getInstance().registerWebapplication(
					 context.getBundle(), "web", "/example", MyServlet.class);
		 } catch (Throwable t) {
		 t.printStackTrace();
		 }
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
	}

}
