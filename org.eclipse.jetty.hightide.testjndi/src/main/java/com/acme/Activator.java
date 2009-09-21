package com.acme;

import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Glue code to be replaced by a nicer web-extender pattern
 * 
 * @author hmalphettes
 */
public class Activator implements BundleActivator {

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		 System.err.println("starting a test-jndi application bundle");
		 try {
			 JettyBootstrapActivator.registerContext(
					 context.getBundle(), "/contexts/test-jndi.xml",
					 JNDITest.class.getName());
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
