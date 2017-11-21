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
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

public class Activator implements BundleActivator {
	
	private static Map data;
	private static Map<Integer,String> bundleStates;
	private static Map<Integer,String> bundleEventStates;
	private OSGiBundleTracker bundleTracker;
	private Properties properties;
	
	public void start(BundleContext context) throws Exception {
		System.out.println("Starting Bundle Tracker");
		int trackStates = Bundle.STARTING | Bundle.STOPPING | Bundle.RESOLVED | Bundle.INSTALLED | Bundle.UNINSTALLED;
		
		//Initialize maps with bundles data and constants.
		initializeData();
		initializeBundleStates();
		initializeBundleEventStates();
		
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
	
	private static void initializeData() {
		data = new HashMap();
	}
	
	private static void initializeBundleStates() {
		bundleStates = new HashMap<Integer,String>();
		bundleStates.put(Bundle.ACTIVE, "ACTIVE");
		bundleStates.put(Bundle.INSTALLED, "INSTALLED");
		bundleStates.put(Bundle.RESOLVED, "RESOLVED");
		bundleStates.put(Bundle.STARTING, "STARTING");
		bundleStates.put(Bundle.STOPPING, "STOPPING");
		bundleStates.put(Bundle.UNINSTALLED, "UNINSTALLED");
	}
	
	private static void initializeBundleEventStates() {
		bundleEventStates = new HashMap<Integer,String>();
		bundleEventStates.put(BundleEvent.INSTALLED, "INSTALLED");
		bundleEventStates.put(BundleEvent.LAZY_ACTIVATION, "LAZY_ACTIVATION");
		bundleEventStates.put(BundleEvent.RESOLVED, "RESOLVED");
		bundleEventStates.put(BundleEvent.STARTED, "STARTED");
		bundleEventStates.put(BundleEvent.STARTING, "STARTING");
		bundleEventStates.put(BundleEvent.STOPPED, "STOPPED");
		bundleEventStates.put(BundleEvent.UNINSTALLED, "UNINSTALLED");
		bundleEventStates.put(BundleEvent.UNRESOLVED, "UNRESOLVED");
		bundleEventStates.put(BundleEvent.UPDATED, "UPDATED");
	}
	
	private static String stateAsString(Bundle bundle) {
		return (bundle == null) ? "NULL" : 
			(bundleStates.containsKey(bundle.getState())) ? bundleStates.get(bundle.getState()) :
			"UNDEFINED";
	}

	private static String typeAsString(BundleEvent event) {
		return (event == null) ? "NULL" : 
			(bundleEventStates.containsKey(event.getType())) ? bundleEventStates.get(event.getType()) :
			"UNDEFINED";
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
				
				System.out.println("[CLASSPATH] " + System.getProperty("user.dir") + ": " + key);
				try {
					ClassLoader bundleClassLoader = (ClassLoader) getBundleClassLoader(bundle,key);
					if(bundleClassLoader != null) {
//						URL mainURL = bundleClassLoader.getResource("");
//						URL otherURL = FileLocator.toFileURL(mainURL);
//						File root = new File(otherURL.toURI());
//						getClassPath(root);
						
						Enumeration<URL> urls = bundleClassLoader.getResources("");
						URL url = null;
						while(urls.hasMoreElements()) {
							url = urls.nextElement();
							getClassPath(new File(FileLocator.toFileURL(url).toURI()));
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				
			}	
			
			
		}
		
		private void getClassPath(File folder) {
			for(File f : folder.listFiles()) {
				if(f.isFile()) {
					System.out.println("[FILE] " + f.getName());
				}
				else {
					System.out.println("[FOLDER] " + f.getName());
					getClassPath(f);
				}
			}
		}
		
		@SuppressWarnings("finally")
		private ClassLoader getBundleClassLoader(Bundle bundle, String bundleKey) {
			ClassLoader classloader = null;
			
			try {
				String path = System.getProperty("user.dir") + "/plugins/" + bundleKey + ".jar";
				InputStream inputStream = new FileInputStream(path);
				JarInputStream jarStream = new JarInputStream(inputStream);
				JarEntry entry = jarStream.getNextJarEntry();
				
				while(entry != null && classloader == null) {
					if(!entry.isDirectory() && entry.getName().endsWith(".class")) {
						String randomClass = (entry.getName().substring(0,entry.getName().lastIndexOf(".class"))).replace("/", ".");
						classloader = bundle.loadClass(randomClass).getClassLoader();
					}
					entry = jarStream.getNextJarEntry();
				}
			}
			catch(Exception e) {
				//This is a bundle fragment. Returns null.
			}
			finally {
				return classloader;
			}
		}
		
		private String createBundleKey(Bundle bundle) {
			return bundle.getSymbolicName() + "_" + bundle.getVersion();
		}
	}

}
