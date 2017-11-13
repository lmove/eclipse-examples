/*******************************************************************************
 * Copyright (c) 2012, EclipseSource Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Elias Volanakis - initial API and implementation
 *******************************************************************************/
package example.osgi.bundletracker;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

public class Activator implements BundleActivator {
	
	private static final int SLOWER_BUNDLES = 10;
	private OSGiBundleTracker bundleTracker;
	private static Map data;
	
	private static void initializeData() {
		data = new HashMap();
	}
	
	private static String stateAsString(Bundle bundle) {
		if (bundle == null) {
			return "null";
		}
		int state = bundle.getState();
		switch (state) {
		case Bundle.ACTIVE:
			return "ACTIVE";
		case Bundle.INSTALLED:
			return "INSTALLED";
		case Bundle.RESOLVED:
			return "RESOLVED";
		case Bundle.STARTING:
			return "STARTING";
		case Bundle.STOPPING:
			return "STOPPING";
		case Bundle.UNINSTALLED:
			return "UNINSTALLED";
		default:
			return "Unknown bundle state: " + state;
		}
	}

	private static String typeAsString(BundleEvent event) {
		if (event == null) {
			return "null";
		}
		int type = event.getType();
		switch (type) {
		case BundleEvent.INSTALLED:
			return "INSTALLED";
		case BundleEvent.LAZY_ACTIVATION:
			return "LAZY_ACTIVATION";
		case BundleEvent.RESOLVED:
			return "RESOLVED";
		case BundleEvent.STARTED:
			return "STARTED";
		case BundleEvent.STARTING:
			return "Starting";
		case BundleEvent.STOPPED:
			return "STOPPED";
		case BundleEvent.UNINSTALLED:
			return "UNINSTALLED";
		case BundleEvent.UNRESOLVED:
			return "UNRESOLVED";
		case BundleEvent.UPDATED:
			return "UPDATED";
		default:
			return "Unknown event type: " + type;
		}
	}

	public void start(BundleContext context) throws Exception {
		System.out.println("Starting Bundle Tracker");
		int trackStates = Bundle.STARTING | Bundle.STOPPING | Bundle.RESOLVED | Bundle.INSTALLED | Bundle.UNINSTALLED;
		
		initializeData();
		bundleTracker = new OSGiBundleTracker(context, trackStates, null);
		bundleTracker.open();
	}

	public void stop(BundleContext context) throws Exception {
		System.out.println("Stopping Bundle Tracker");
		String[][] m = getTimeConsumingBundles();
		printMap(m);
		System.out.println("[SIZE] " + data.size());
		bundleTracker.close();
		bundleTracker = null;
	}
	
	private String[][] getTimeConsumingBundles() {
		String[][] maxValues = initializeSlowerBundles();
		Iterator it = data.entrySet().iterator();
		
		while(it.hasNext()) {
			Map.Entry s = (Map.Entry) it.next();
			String[] v = (String[]) s.getValue();
			boolean found = false;
			String[] current = null;
			
			for(int i = 0; i < maxValues.length; i++) {
				if(!found && (Integer.parseInt(v[2]) > Integer.parseInt(maxValues[i][1]))) {
					current = (String[]) maxValues[i].clone();
					maxValues[i][0] = (String) s.getKey();
					maxValues[i][1] = v[2];
					found = true; 
				}
				else if(found && current != null) {
					String[] temp = (String[]) maxValues[i].clone();
					maxValues[i] = current;
					current = temp;
				}
			}
		}
		
		return maxValues;
	}
	
	private String[][] initializeSlowerBundles() {
		String[][] maxValues = new String[SLOWER_BUNDLES][2];
		for (int i = 0; i < maxValues.length; i++) {
			maxValues[i][0] = "null";
			maxValues[i][1] = "0";
		}
		return maxValues;
	}
	
	private void printMap(String[][] m) {
		for(int i = 0; i < m.length; i++) {
			System.out.println("[TOP]" + m[i][0] + " - " + m[i][1]);
		}
	}
	
	private static final class OSGiBundleTracker extends BundleTracker {

		public OSGiBundleTracker(BundleContext context, int stateMask, BundleTrackerCustomizer customizer) {
			super(context, stateMask, customizer);
		}

		public Object addingBundle(Bundle bundle, BundleEvent event) {
			data.put(createBundleKey(bundle), new String[]{""+System.currentTimeMillis(),"0","0"});
			System.out.println("[ADD] " + bundle.getSymbolicName() + " - " + System.currentTimeMillis() + " - " + typeAsString(event) + " - " + stateAsString(bundle));
			return bundle;
		}
		
		public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
		}
		
		public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
			if(event.getType() == BundleEvent.RESOLVED) {
				String key = createBundleKey(bundle);
				String[] time = (String[]) data.get(key);
				time[1] = "" + System.currentTimeMillis();
				time[2] = "" + (Long.parseLong(time[1]) - Long.parseLong(time[0]));
				data.put(key, time);
				
				System.out.println("[MOD] " + bundle.getSymbolicName() + " - " + System.currentTimeMillis() + " - " + typeAsString(event) + " - " + stateAsString(bundle));
			}	
		}
		
		private String createBundleKey(Bundle bundle) {
			return bundle.getSymbolicName() + "_" + bundle.getVersion();
		}
	}

}
