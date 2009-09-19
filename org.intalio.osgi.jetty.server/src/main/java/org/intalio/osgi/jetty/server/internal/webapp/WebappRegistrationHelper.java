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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.jasper.compiler.TldLocationsCache;
import org.eclipse.jetty.deploy.ConfigurationManager;
import org.eclipse.jetty.deploy.ContextDeployer;
import org.eclipse.jetty.deploy.WebAppDeployer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.intalio.osgi.jetty.server.JettyBootstrapActivator;
import org.intalio.osgi.jetty.server.internal.jsp.TldLocatableURLClassloader;
import org.intalio.osgi.jetty.server.internal.jsp.TldLocatableURLClassloaderWithInsertedJettyClassloader;
import org.intalio.osgi.jetty.server.utils.FileLocatorHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.xml.sax.SAXException;

/**
 * Bridges the traditional web-application deployers: {@link WebAppDeployer} and {@link ContextDeployer}
 * with the OSGi lifecycle where applications are managed as OSGi-bundles.
 * <p>
 * Helper methods to register a bundle that is a web-application or a context.
 * It is deployed as if the server was using its WebAppDeployer or ContextDeployer
 * as configured in its etc/jetty.xml file.
 * Well as close as possible to that.
 * </p>
 * Limitations:
 * <ul>
 * <li>all bundles that contain a webapp are assumed unzipped.</li>
 * </ul>
 * @author hmalphettes
 */
public class WebappRegistrationHelper {
	
	private Server _server;
	private ContextDeployer _ctxtDeployer;
	private WebAppDeployer _webappDeployer;
	private ContextHandlerCollection _ctxtHandler;
	
	public WebappRegistrationHelper(Server server) {
		_server = server;
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
	 * @See {@link WebAppDeployer#scan()}
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
			TldLocatableURLClassloader composite =
				createContextClassLoader(contributor, classInBundle);
			//configure with access to all jetty classes and also all the classes
			//that the contributor gives access to.
			Thread.currentThread().setContextClassLoader(composite);
			
			WebAppContext context = new WebAppContext(webapp.getAbsolutePath(), contextPath);
			
			WebXmlConfiguration webXml = new WebXmlConfiguration();
			webXml.configure(context);
			
			JettyWebXmlConfiguration jettyXml = new JettyWebXmlConfiguration();
			jettyXml.configure(context);
			
			configureWebAppContext(context);
			
			//ok now register this webapp. we checked when we started jetty that there
			//was at least one such handler for webapps.
			_ctxtHandler.addHandler(context);
			
			configureContextClassLoader(context, composite);
			
			context.start();
		
		} finally {
			Thread.currentThread().setContextClassLoader(contextCl);
		}
		
	}
	
	public void unregister(String contextPath) {
		//TODO
	}
	
	
	
	/**
	 * This type of registration relies on jetty's complete context xml file.
	 * Context encompasses jndi and all other things.
	 * This makes the definition of the webapp a lot more self-contained.
	 * 
	 * @param webapp
	 * @param contextPath
	 * @param classInBundle
	 * @throws Exception
	 */
	public synchronized void registerContext(Bundle contributor, File contextFile,
			Class<?> classInBundle) throws Exception {
		ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
		try {
			//make sure we provide access to all the jetty bundles by going through this bundle.
			TldLocatableURLClassloader composite =
				createContextClassLoader(contributor, classInBundle);
			//configure with access to all jetty classes and also all the classes
			//that the contributor gives access to.
			Thread.currentThread().setContextClassLoader(composite);
			ContextHandler context = createContextHandler(contributor, contextFile);
	        //[H]extra work for the path to the file:
	        if (context instanceof WebAppContext) {
	        	WebAppContext wah = (WebAppContext)context;
	        	Resource.newResource(wah.getWar());
	        }
	
	        //ok now register this webapp. we checked when we started jetty that there
			//was at least one such handler for webapps.
			_ctxtHandler.addHandler(context);
			
			configureContextClassLoader(context, composite);
			
			context.start();
		} finally {
			Thread.currentThread().setContextClassLoader(contextCl);
		}

	}
	

	/**
	 * TODO: right now only the jetty-jsp bundle is scanned for common taglibs.
	 * Should support a way to plug more bundles that contain taglibs.
	 * 
	 * The jasper TldScanner expects a URLClassloader to parse a jar for the
	 * /META-INF/*.tld it may contain.
	 * We place the bundles that we know contain such tag-libraries.
	 * Please note that it will work if and only if the bundle is a jar (!)
	 * Currently we just hardcode the bundle that contains the jstl implemenation.
	 * 
	 * A workaround when the tld cannot be parsed with this method is to copy and paste
	 * it inside the WEB-INF of the webapplication where it is used.
	 * 
	 * Support only 2 types of packaging for the bundle:
	 * - the bundle is a jar (recommended for runtime.)
	 * - the bundle is a folder and contain jars in the root and/or in the lib folder
	 * (nice for PDE developement situations) 
	 * Unsupported: the bundle is a jar that embeds more jars.
	 * 
	 * @return
	 * @throws Exception
	 */
	private URL[] getJarsWithTlds() throws Exception {
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
	
	/**
	 * Must be called after the server is configured.
	 * 
	 * Locate the actual instance of the ContextDeployer and WebAppDeployer
	 *  that was created when configuring the server through jetty.xml.
	 * If there is no such thing it won't be possible to deploy webapps from a context
	 * and we throw IllegalStateExceptions.
	 */
	public void init() {
		
        //[Hugues] if no jndi is setup let's do it:
		if (System.getProperty("java.naming.factory.initial") == null) {
			System.setProperty("java.naming.factory.initial", "org.eclipse.jetty.jndi.InitialContextFactory");
		}
		if (System.getProperty("java.naming.factory.url.pkgs") == null) {
			System.setProperty("java.naming.factory.url.pkgs", "org.eclipse.jetty.jndi");
		}

		
		_ctxtHandler = (ContextHandlerCollection)_server
			.getChildHandlerByClass(ContextHandlerCollection.class);
		if (_ctxtHandler == null) {
			throw new IllegalStateException(
					"ERROR: No ContextHandlerCollection was configured" +
					" with the server to add applications to." +
					"Using a default one is not supported at" +
					" this point. " + " Please review the jetty.xml file used.");
		}
		List<ContextDeployer> ctxtDeployers = _server.getBeans(ContextDeployer.class);
		
		if (ctxtDeployers == null || ctxtDeployers.isEmpty()) {
			System.err.println("Warn: No ContextDeployer was configured" +
					" with the server. Using a default one is not supported at" +
					" this point. " + " Please review the jetty.xml file used.");
		} else {
			_ctxtDeployer = ctxtDeployers.get(0);
		}
		List<WebAppDeployer> wDeployers = _server.getBeans(WebAppDeployer.class);
		
		if (wDeployers == null || wDeployers.isEmpty()) {
			System.err.println("Warn: No WebappDeployer was configured" +
					" with the server. Using a default one is not supported at" +
					" this point. " + " Please review the jetty.xml file used.");
		} else {
			_webappDeployer = (WebAppDeployer)wDeployers.get(0);
		}
	}

	/**
	 * Applies the properties of WebAppDeployer as defined in jetty.xml.
	 * @see {WebAppDeployer#scan} around the comment <code>// configure it</code>
	 */
	protected void configureWebAppContext(WebAppContext wah) {
		// configure it
        //wah.setContextPath(context);
		String[] _configurationClasses = _webappDeployer.getConfigurationClasses();
		String _defaultsDescriptor = _webappDeployer.getDefaultsDescriptor();
		boolean _parentLoaderPriority = _webappDeployer.isParentLoaderPriority();
        AttributesMap _contextAttributes = getWebAppDeployerContextAttributes();
		
		if (_configurationClasses!=null)
            wah.setConfigurationClasses(_configurationClasses);
        if (_defaultsDescriptor!=null)
            wah.setDefaultsDescriptor(_defaultsDescriptor);
        //wah.setExtractWAR(_extract);//[H]should we force to extract ?
        //wah.setWar(app.toString());//[H]should we force to extract ?
        wah.setParentLoaderPriority(_parentLoaderPriority);
        
        //set up any contextAttributes
        wah.setAttributes(new AttributesMap(_contextAttributes));
		
	}
	
	/**
	 * @See {@link ContextDeployer#scan}
	 * @param contextFile
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected ContextHandler createContextHandler(Bundle bundle, File contextFile) {
		
		/*
		 * Do something identical to what the ContextDeployer would have done:
		    XmlConfiguration xmlConfiguration=new XmlConfiguration(resource.getURL());
	        HashMap properties = new HashMap();
	        properties.put("Server", _contexts.getServer());
	        if (_configMgr!=null)
	            properties.putAll(_configMgr.getProperties());
	           
	        xmlConfiguration.setProperties(properties);
	        ContextHandler context=(ContextHandler)xmlConfiguration.configure();
	        context.setAttributes(new AttributesMap(_contextAttributes));

		 */
		ConfigurationManager _configMgr = getContextDeployerConfigurationManager();
        AttributesMap _contextAttributes = getContextDeployerContextAttributes();
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(contextFile));
			XmlConfiguration xmlConfiguration=new XmlConfiguration(in);
	        HashMap<String,Object> properties = new HashMap<String,Object>();
	        properties.put("Server", _server);
	        if (_configMgr!=null) {
	            properties.putAll(_configMgr.getProperties());
	        }
	        //insert the bundle's location as a property.
	        setThisBundleHomeProperty(bundle, properties);
	        xmlConfiguration.setProperties(properties);
	        ContextHandler context=(ContextHandler)xmlConfiguration.configure();
	        context.setAttributes(new AttributesMap(_contextAttributes));
	        return context;
		} catch (FileNotFoundException e) {
			return null;
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (in != null) try { in.close(); } catch (IOException ioe) {}
		}
		return null;
	}
	
	/**
	 * Configure a classloader onto the context.
	 * If the context is a WebAppContext, build a WebAppClassLoader
	 * that has access to all the jetty classes thanks to the classloader of 
	 * the JettyBootStrapper bundle and also has access to the classloader of
	 * the bundle that defines this context.
	 * <p>
	 * If the context is not a WebAppContext, same but with a simpler URLClassLoader.
	 * Note that the URLClassLoader is pretty much fake:
	 * it delegate all actual classloading to the parent classloaders.
	 * </p>
	 * <p>
	 * The URL[] returned by the URLClassLoader create contained specifically 
	 * the jars that some j2ee tools expect and look into. For example the jars
	 * that contain tld files for jasper's jstl support.
	 * </p>
	 * @param context
	 * @param contributor
	 * @param webapp
	 * @param contextPath
	 * @param classInBundle
	 * @throws Exception
	 */
	protected void configureContextClassLoader(ContextHandler context,
			TldLocatableURLClassloader composite) throws Exception {
		if (context instanceof WebAppContext) {
		    WebAppClassLoader wcl =
		    	new WebAppClassLoader(composite, (WebAppContext) context);
		    //addJarsWithTlds(wcl);
		    context.setClassLoader(wcl);
        } else {
        	context.setClassLoader(composite);
        }

	}
	
	
	protected TldLocatableURLClassloader createContextClassLoader(
			Bundle contributor, Class<?> classInBundle) throws Exception {
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
	//	WebAppClassLoader cl = new WebAppClassLoader(classInBundle.getClassLoader(), context);
	//	context.setClassLoader(cl);
	
	    	//Make all of the jetty's classes available to the webapplication classloader
	    	//also add the contributing bundle's classloader to give access to osgi to
	    	//the contributed webapp.
	        ClassLoader osgiCl = contributor.loadClass(bundleClassName).getClassLoader();
	        TldLocatableURLClassloader composite =
	        	new TldLocatableURLClassloaderWithInsertedJettyClassloader(
		    		JettyBootstrapActivator.class.getClassLoader(), osgiCl,
		    		getJarsWithTlds());
	        return composite;
	    } else {
	    	//Make all of the jetty's classes available to the webapplication classloader
	    	TldLocatableURLClassloader composite = new TldLocatableURLClassloader(
		    		JettyBootstrapActivator.class.getClassLoader(),
		    		getJarsWithTlds());
	    	return composite;
		    
	    }

	}
	
	/**
	 * Set the property &quot;this.bundle.install&quot; to point to the location of the bundle.
	 * Useful when <SystemProperty name="this.bundle.home"/> is used.
	 */
	private void setThisBundleHomeProperty(Bundle bundle, HashMap<String,Object> properties) {
		try {
			File location = FileLocatorHelper.getBundleInstallLocation(bundle);
			properties.put("this.bundle.install", location.getCanonicalPath());
		} catch (Throwable t) {
			System.err.println("Unable to set 'this.bundle.install' " +
					" for the bundle " + bundle.getSymbolicName());
			t.printStackTrace();
		}
	}
	
	
	//some private suff in ContextDeployer that we need to
	//be faithful to the ContextDeployer definition created in etc/jetty.xml
	//kindly ask to have a public getter for those?
	private static Field CONTEXT_DEPLOYER_CONFIGURATION_MANAGER_FIELD = null;
	private static Field CONTEXT_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD = null;
	private ConfigurationManager getContextDeployerConfigurationManager() {
		try {
			if (CONTEXT_DEPLOYER_CONFIGURATION_MANAGER_FIELD == null) {
				CONTEXT_DEPLOYER_CONFIGURATION_MANAGER_FIELD =
					ContextDeployer.class.getDeclaredField("_configMgr");
				CONTEXT_DEPLOYER_CONFIGURATION_MANAGER_FIELD.setAccessible(true);
			}
			return (ConfigurationManager) CONTEXT_DEPLOYER_CONFIGURATION_MANAGER_FIELD.get(_ctxtDeployer);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}
	private AttributesMap getContextDeployerContextAttributes() {
		try {
			if (CONTEXT_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD == null) {
				CONTEXT_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD =
					ContextDeployer.class.getDeclaredField("_contextAttributes");
				CONTEXT_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD.setAccessible(true);
			}
			return (AttributesMap) CONTEXT_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD.get(_ctxtDeployer);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}
	private static Field WEBAPP_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD = null;
	private AttributesMap getWebAppDeployerContextAttributes() {
		try {
			if (WEBAPP_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD == null) {
				WEBAPP_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD =
					WebAppDeployer.class.getDeclaredField("_contextAttributes");
				WEBAPP_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD.setAccessible(true);
			}
			return (AttributesMap) WEBAPP_DEPLOYER_CONTEXT_ATTRIBUTES_FIELD.get(_ctxtDeployer);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return null;
	}

	
	
	
}
