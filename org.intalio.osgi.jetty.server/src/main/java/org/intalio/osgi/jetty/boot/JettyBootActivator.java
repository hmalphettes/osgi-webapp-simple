package org.intalio.osgi.jetty.boot;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.webapp.JettyWebXmlConfiguration;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
import org.mortbay.xml.XmlConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Activates a jetty server in the same manner almost as if it was started outside of osgi.
 * 
 * @author hmalphettes
 * @author Intalio Inc.
 */
public class JettyBootActivator implements BundleActivator, WebapplicationService {
	
	private static JettyBootActivator INSTANCE = null;
	
	public static JettyBootActivator getInstance() {
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
		_installLocation = getBundleInstallLocation(context);
		
		String jettyHome = System.getProperty("jetty.home");
		if (jettyHome == null || jettyHome.length() == 0) {
			System.setProperty("jetty.home", _installLocation.getAbsolutePath() + "/jetty-6.1.19");
		}
		String jettyLogs = System.getProperty("jetty.logs");
		if (jettyLogs == null || jettyLogs.length() == 0) {
			System.setProperty("jetty.logs", System.getProperty("jetty.home") + "/logs");
		}
		try {
			_server = new Server();
			XmlConfiguration config = new XmlConfiguration(new FileInputStream(
					System.getProperty("jetty.home") + "/etc/jetty.xml"));
			config.configure(_server);
			_server.start();
//					_server.join();
		} catch (Throwable t) {
			t.printStackTrace();
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
	
	
	
	private static Field BUNDLE_ENTRY_FIELD = null;
	private static Field FILE_FIELD = null;
	public static File getBundleInstallLocation(BundleContext context) throws Exception {
		//String installedBundles = System.getProperty("osgi.bundles");
		//grab the MANIFEST.MF's url
		//and then do what it takes.
		URL url = context.getBundle().getEntry("/META-INF/MANIFEST.MF");
//		System.err.println(url.toString() + " " + url.toURI() + " " + url.getProtocol());
		if (url.getProtocol().equals("file")) {
			//this is the case with Felix and maybe other OSGI frameworks
			//should make sure it is not a jar.
			return new File(url.toURI()).getParentFile().getParentFile();
		} else if (url.getProtocol().equals("bundleentry")) {
			//say hello to equinox who has its own protocole.
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
	public void registerWebapplication(BundleContext context, String webappFolderPath,
			String contextPath, Class<?> classInBundle) throws Exception {
		File bundleInstall = getBundleInstallLocation(context);
		File webapp = webappFolderPath != null && webappFolderPath.length() != 0
			? new File(bundleInstall, webappFolderPath) : bundleInstall;
		if (!webapp.exists()) {
			throw new IllegalArgumentException("Unable to locate " + contextPath
					+ " inside ");
		}
		registerWebapplication(webapp, contextPath, classInBundle);
	}

	/**
	 * 
	 * @param webapp
	 * @param contextPath
	 * @param classInBundle
	 * @throws Exception
	 */
	public void registerWebapplication(File webapp, String contextPath, Class<?> classInBundle) throws Exception {
		WebAppContext context = new WebAppContext(webapp.getAbsolutePath(), contextPath);
		context.setDefaultsDescriptor(System.getProperty("jetty.home") + "/etc/webdefault.xml");
		
		WebXmlConfiguration webXml = new WebXmlConfiguration();
		webXml.setWebAppContext(context);
		
		JettyWebXmlConfiguration jettyXml = new JettyWebXmlConfiguration();
		jettyXml.setWebAppContext(context);
		jettyXml.configureWebApp();
		webXml.configureWebApp();
		
		//ok now make sure the server knows this webapp:
		((HandlerCollection)_server.getHandlers()[0]).addHandler(context);
		
		WebAppClassLoader cl = new WebAppClassLoader(classInBundle.getClassLoader(), context);
		context.setClassLoader(cl);
		
		context.start();
		
	}
	
	public void unregister(String contextPath) {
		//TODO
	}

}
