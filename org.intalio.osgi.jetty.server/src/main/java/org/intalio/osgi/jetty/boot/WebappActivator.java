package org.intalio.osgi.jetty.boot;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.log.LogService;

/**
 * The extender pattern applied to register new webapplications packaged
 * as osgi-bundles.
 * 
 * @author hmalphettes
 * @author Intalio Inc
 */
public class WebappActivator implements BundleListener {
	
	BundleContext context;
    Registry registry = new Registry();

	protected void activate(ComponentContext cc) {
		this.context = cc.getBundleContext();
		context.addBundleListener(this);
		Bundle bundles[] = context.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			if ((bundles[i].getState() & (Bundle.STARTING | Bundle.ACTIVE)) != 0)
				registry.register(bundles[i]);
		}
	}

    protected void deactivate(ComponentContext context)
        throws Exception {
      this.context.removeBundleListener(this);
      registry.close();
    }

    public void bundleChanged(BundleEvent event) {
      switch (event.getType()) {
      case BundleEvent.STARTED:
        registry.register(event.getBundle());
        break;

      case BundleEvent.STOPPED:
        registry.unregister(event.getBundle());
        break;
      }
    }

    public void setWebapplications(WebapplicationService webapps) {
      registry.webapps = webapps;
    }

    public void setLog(LogService log) {
      registry.log = log;
    }


}
