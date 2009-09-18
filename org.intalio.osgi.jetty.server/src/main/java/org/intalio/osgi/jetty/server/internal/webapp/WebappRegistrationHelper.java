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

import org.apache.jasper.compiler.TldLocationsCache;
import org.eclipse.jetty.deploy.ConfigurationManager;
import org.eclipse.jetty.deploy.ContextDeployer;
import org.eclipse.jetty.deploy.WebAppDeployer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.AttributesMap;
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
 * Helper methods to register a bundle that is a web-application or a context.
 * It is deployed as if the server was using its WebAppDeployer or ContextDeployer.
 * Well as close as possible to that.
 * 
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
	
	public WebappRegistrationHelper(Server server) {
		_server = server;
		initDeployers();
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
			Thread.currentThread().setContextClassLoader(JettyBootstrapActivator.class.getClassLoader());
			
			WebAppContext context = new WebAppContext(webapp.getAbsolutePath(), contextPath);
			
			WebXmlConfiguration webXml = new WebXmlConfiguration();
			webXml.configure(context);
			
			JettyWebXmlConfiguration jettyXml = new JettyWebXmlConfiguration();
			jettyXml.configure(context);
			
			configureWebAppContext(context);

			
			//ok now register this webapp. we checked when we started jetty that there
			//was at least one such handler for webapps.
			ContextHandlerCollection ctxtHandler = (ContextHandlerCollection)_server
					.getChildHandlerByClass(ContextHandlerCollection.class);
			ctxtHandler.addHandler(context);
			
	        //[Hugues] if we want the webapp to be able to load classes inside osgi
	        //we must get a hold of the bundle's classloader.
	        //I have not found a way to do this directly from the bundle object unfortunately.
	        //As a workaround, we require the developer to declare the class name of
	        //an object that is defined inside the bundle.
	        //TODO: find a way to get the bundle's classloader directly from the org.osgi.framework.Bundle object (?)
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
//    		WebAppClassLoader cl = new WebAppClassLoader(classInBundle.getClassLoader(), context);
//    		context.setClassLoader(cl);
	
	        	//Make all of the jetty's classes available to the webapplication classloader
	        	//also add the contributing bundle's classloader to give access to osgi to
	        	//the contributed webapp.
	            ClassLoader osgiCl = contributor.loadClass(bundleClassName).getClassLoader();
	            ClassLoader composite = //new TwinClassLoaders(
	            	new TldLocatableURLClassloaderWithInsertedJettyClassloader(
			    		JettyBootstrapActivator.class.getClassLoader(), osgiCl,
			    		getJarsWithTlds());
			    WebAppClassLoader wcl = new WebAppClassLoader(composite, context);
			    //addJarsWithTlds(wcl);
			    context.setClassLoader(wcl);
	        } else {
	        	//Make all of the jetty's classes available to the webapplication classloader
	        	WebAppClassLoader wcl = new WebAppClassLoader(
	        			new TldLocatableURLClassloader(
	    			    		JettyBootstrapActivator.class.getClassLoader(),
	    			    		getJarsWithTlds()), context);
	        	//addJarsWithTlds(wcl);
			    context.setClassLoader(wcl);
	        }
			
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
	public void registerContext(Bundle contributor, File contextFile,
			Class<?> classInBundle) throws Exception {
		
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
	 * Locate the actual instance of the ContextDeployer and WebAppDeployer
	 *  that was created when configuring the server through jetty.xml.
	 * If there is no such thing it won't be possible to deploy webapps from a context
	 * and we throw IllegalStateExceptions.
	 */
	private void initDeployers() {
		_ctxtDeployer = (ContextDeployer)_server.getBeans(ContextDeployer.class);
		_webappDeployer = (WebAppDeployer)_server.getBeans(WebAppDeployer.class);
		if (_ctxtDeployer == null) {
			System.err.println("No ContextDeployer was configured" +
					" with the server. Using a default one is not supported at" +
					" this point. " + " Please reveiw the jetty.xml file used.");
		}
		if (_webappDeployer == null) {
			System.err.println("No WebappDeployer was configured" +
					" with the server. Using a default one is not supported at" +
					" this point. " + " Please reveiw the jetty.xml file used.");
		}
	}

	/**
	 * 
	 * @return
	 */
	protected void configureWebAppContext(WebAppContext wah) {
		/*
        // configure it
        wah.setContextPath(context);
        if (_configurationClasses!=null)
            wah.setConfigurationClasses(_configurationClasses);
        if (_defaultsDescriptor!=null)
            wah.setDefaultsDescriptor(_defaultsDescriptor);
        wah.setExtractWAR(_extract);
        wah.setWar(app.toString());
        wah.setParentLoaderPriority(_parentLoaderPriority);
        
        //set up any contextAttributes
        wah.setAttributes(new AttributesMap(_contextAttributes));
		*/
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
        //wah.setExtractWAR(_extract);
        //wah.setWar(app.toString());
        wah.setParentLoaderPriority(_parentLoaderPriority);
        
        //set up any contextAttributes
        wah.setAttributes(new AttributesMap(_contextAttributes));
		
	}
	
	/**
	 * @See {@link ContextDeployer#scan}
	 * @param contextFile
	 * @return
	 */
	protected ContextHandler createContextHandler(File contextFile) {
		
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
	
	
	protected void configureContextClassLoader(ContextHandler context) {
		
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
