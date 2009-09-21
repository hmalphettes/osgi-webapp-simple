package org.intalio.osgi.examplecontextapp;

import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		 System.err.println("starting a context bundle");
		 try {
			 JettyBootstrapActivator.registerContext(
					 context.getBundle(), "contexts/examplecontext.xml",
					 MyServlet.class.getName());
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
