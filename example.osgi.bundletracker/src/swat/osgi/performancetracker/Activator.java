/*******************************************************************************
 * Copyright (c) 2012, EclipseSource Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Elias Volanakis - initial API and implementation
 *     Lina Ochoa - modifications to track performance and classpath size
 *******************************************************************************/

package swat.osgi.performancetracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

public class Activator implements BundleActivator {

	//------------------------------------------------------------
	// Constants
	//------------------------------------------------------------

	private static final String CLASS_EXTENSION = ".class";
	private static final String JAR_EXTENSION = ".jar";
	private static final String DATA_FOLDER = "framework-metadata";
	private static final String CSV_SEPARATOR = ",";


	//------------------------------------------------------------
	// Fields
	//------------------------------------------------------------

	private static Map<String,Long[]> performanceData;
	private static Map<Integer,String> bundleStates;
	private static Map<Integer,String> bundleEventStates;
	private OSGiBundleTracker bundleTracker;


	//------------------------------------------------------------
	// Methods
	//------------------------------------------------------------

	/**
	 * Sets the tracked states and the employed data structures.
	 * The OSGi bundle tracker is initialized.
	 */
	public void start(BundleContext context) throws Exception {
		System.out.println("Starting Bundle Tracker");
		int trackStates = Bundle.STARTING | Bundle.STOPPING | Bundle.RESOLVED | Bundle.INSTALLED | Bundle.UNINSTALLED;

		//Initialize maps with bundles data and constants.
		performanceData = new HashMap<String, Long[]>();
		initializeBundleStates();
		initializeBundleEventStates();

		bundleTracker = new OSGiBundleTracker(context, trackStates, null);
		bundleTracker.open();
	}

	/**
	 * Gathers performance and classpath size information related
	 * to the tracked bundles of the framework. 
	 */
	public void stop(BundleContext context) throws Exception {
		try {
			System.out.println("Stopping Performance Tracker");
			performanceToCSV();
			System.out.println("Metadata was printed.");

			bundleTracker.close();
			bundleTracker = null;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initializes the bundle states map.
	 */
	private static void initializeBundleStates() {
		bundleStates = new HashMap<Integer,String>();
		bundleStates.put(Bundle.ACTIVE, "ACTIVE");
		bundleStates.put(Bundle.INSTALLED, "INSTALLED");
		bundleStates.put(Bundle.RESOLVED, "RESOLVED");
		bundleStates.put(Bundle.STARTING, "STARTING");
		bundleStates.put(Bundle.STOPPING, "STOPPING");
		bundleStates.put(Bundle.UNINSTALLED, "UNINSTALLED");
	}

	/**
	 * Initializes the bundle event states map.
	 */
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

	/**
	 * Returns a string representing the state of the bundle.
	 */
	private static String stateAsString(Bundle bundle) {
		return (bundle == null) ? "NULL" : 
			(bundleStates.containsKey(bundle.getState())) ? bundleStates.get(bundle.getState()) :
				"UNDEFINED";
	}

	/**
	 * Returns a string representing the state of the bundle event.
	 */
	private static String typeAsString(BundleEvent event) {
		return (event == null) ? "NULL" : 
			(bundleEventStates.containsKey(event.getType())) ? bundleEventStates.get(event.getType()) :
				"UNDEFINED";
	}

	/**
	 * Creates a CSV file with the resolving performance of 
	 * resolved bundles.
	 */
	private void performanceToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,Resolving Time\n");

		Set<Entry<String, Long[]>> performance = performanceData.entrySet();
		Iterator<Entry<String, Long[]>> it = performance.iterator();
		Entry<String,Long[]> entry = null;
		while(it.hasNext()) {
			entry = it.next();
			builder.append(entry.getKey() + CSV_SEPARATOR + entry.getValue()[2] + '\n');
		}
		
		writeFile(DATA_FOLDER + "/performance-info.csv", builder.toString());
	}
	
	private void writeFile(String path, String content) {
		try {
			File file = new File(path);
			file.getParentFile().mkdirs();

			PrintWriter writer = new PrintWriter(file);
			writer.write(content);
			writer.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a bundle identifier: symbolicName_version
	 */
	protected static String createBundleKey(Bundle bundle) {
		return bundle.getSymbolicName() + "_" + bundle.getVersion();
	}
	
	
	//------------------------------------------------------------
	// Nested Class
	//------------------------------------------------------------

	private static final class OSGiBundleTracker extends BundleTracker {

		//------------------------------------------------------------
		// Methods
		//------------------------------------------------------------

		public OSGiBundleTracker(BundleContext context, int stateMask, BundleTrackerCustomizer customizer) {
			super(context, stateMask, customizer);
		}

		/**
		 * Adds information to the performance data structure.
		 * For the Double[]: [InstalledTime, ResolvedTime, ResolvingTimeDelta]
		 * Sets InstalledTime slot.
		 */
		public Object addingBundle(Bundle bundle, BundleEvent event) {
			String key = createBundleKey(bundle);
			performanceData.put(key, new Long[]{System.nanoTime(), 0L, -1L});
			//System.out.println("[ADD] " + key + " - STATE: " + stateAsString(bundle));
			return bundle;
		}

		/**
		 * Sets bundle resolution performance.
		 */
		public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
			String key = createBundleKey(bundle);

			if(event.getType() == BundleEvent.RESOLVED) {
				//Updates performance data structure.
				Long[] time = performanceData.get(key);
				time[1] = System.nanoTime();
				time[2] = time[1] - time[0];
				performanceData.put(key, time);
			}	
			//System.out.println("[MODIFIED] " + key + " - STATE: " + stateAsString(bundle));
		}
	}
}
