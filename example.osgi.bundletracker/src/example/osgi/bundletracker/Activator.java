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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

public class Activator implements BundleActivator {
	private static Map data;
	private OSGiBundleTracker bundleTracker;
	private Properties properties;
	
	
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
		try {
			System.out.println("Stopping Bundle Tracker");
			initializePropertiesReader();
			
			String[][] m = null;
			String mode = properties.getProperty("mode");
			
			if(mode.equals("SMELLY")) {
				m = getSmellyBundles();
			}
			else {
				m = getSlowestBundles();
			}
			printMap(m);
			System.out.println("[SIZE] " + data.size());
			
			bundleTracker.close();
			bundleTracker = null;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	private void initializePropertiesReader() {
		properties = new Properties();
		try {
			InputStream is = new FileInputStream("tracker.properties");
			properties.load(is);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private String[][] getSmellyBundles() {
		String[][] maxValues = initializeTopBundles();
		
		for(int i = 0; i < maxValues.length; i++) {
			String key = properties.getProperty("bundles[" + i + "]");
			String[] value = (String[]) data.get(key);
			System.out.println(key + " - " + value);
			maxValues[i][0] = key;
			maxValues[i][1] = value[2];
		}
		return maxValues;
	}
	
	private String[][] getSlowestBundles() {
		String[][] maxValues = initializeTopBundles();
		Iterator it = data.entrySet().iterator();
		
		while(it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String[] value = (String[]) entry.getValue();
			boolean found = false;
			String[] current = null;
			
			for(int i = 0; i < maxValues.length; i++) {
				if(!found && (Integer.parseInt(value[2]) > Integer.parseInt(maxValues[i][1]))) {
					current = (String[]) maxValues[i].clone();
					maxValues[i][0] = (String) entry.getKey();
					maxValues[i][1] = value[2];
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
	
	private String[][] initializeTopBundles() {
		int bundles = Integer.parseInt(properties.getProperty("bundles"));
		String[][] maxValues = new String[bundles][2];
		
		for (int i = 0; i < maxValues.length; i++) {
			maxValues[i][0] = "null";
			maxValues[i][1] = "0";
		}
		return maxValues;
	}
	
	private void printMap(String[][] m) {
		if(m != null) {
			for(int i = 0; i < m.length; i++) {
				System.out.println("[TOP]" + m[i][0] + " - " + m[i][1]);
			}
		}
	}
	
	private static final class OSGiBundleTracker extends BundleTracker {

		public OSGiBundleTracker(BundleContext context, int stateMask, BundleTrackerCustomizer customizer) {
			super(context, stateMask, customizer);
		}

		public Object addingBundle(Bundle bundle, BundleEvent event) {
			data.put(createBundleKey(bundle), new String[]{""+System.currentTimeMillis(),"0","0"});
			System.out.println("[ADD] " + createBundleKey(bundle) + " - " + System.currentTimeMillis() + " - " + typeAsString(event) + " - " + stateAsString(bundle));
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
			}	
		}
		
		private String createBundleKey(Bundle bundle) {
			return bundle.getSymbolicName() + "_" + bundle.getVersion();
		}
	}

}
