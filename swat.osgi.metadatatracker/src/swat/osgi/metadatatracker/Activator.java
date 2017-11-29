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

package swat.osgi.metadatatracker;

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

	private static Map<String,Integer> resolvedData;
	private static Map<String,Integer> classpathData;
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
		System.out.println("Starting Metadata Tracker");
		int trackStates = Bundle.STARTING | Bundle.STOPPING | Bundle.RESOLVED | Bundle.INSTALLED | Bundle.UNINSTALLED;

		//Initialize maps with bundles data and constants.
		initializeData();
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
			System.out.println("Stopping Metadata Tracker");
			
			bundleStatesToCSV();
			classpathToCSV();
			resolvedBundlesToCSV();
			
			System.out.println("Metadata was printed.");

			bundleTracker.close();
			bundleTracker = null;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initializes data structures related to performance and
	 * classpath size information.
	 */
	private static void initializeData() {
		classpathData = new HashMap<String,Integer>();
		resolvedData = new HashMap<String, Integer>();
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
	 * Creates a CSV file with the classpath size of resolved
	 * and non-fragment bundles.
	 */
	private void classpathToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,Classpath Size\n");

		Set<Entry<String,Integer>> classpaths = classpathData.entrySet();
		Iterator<Entry<String,Integer>> it = classpaths.iterator();
		Entry<String,Integer> entry = null;
		while(it.hasNext()) {
			entry = it.next();
			builder.append(entry.getKey() + CSV_SEPARATOR + entry.getValue() + '\n');
		}
		
		writeFile(DATA_FOLDER + "/classpath-info.csv", builder.toString());
	}

	/**
	 * Creates a CSV file with the resolving performance of 
	 * resolved bundles.
	 */
	private void resolvedBundlesToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,Resolved Bundles\n");

		Set<Entry<String,Integer>> performance = resolvedData.entrySet();
		Iterator<Entry<String,Integer>> it = performance.iterator();
		Entry<String,Integer> entry = null;
		while(it.hasNext()) {
			entry = it.next();
			builder.append(entry.getKey() + CSV_SEPARATOR + entry.getValue() + '\n');
		}
		
		writeFile(DATA_FOLDER + "/resolved-bundles-info.csv", builder.toString());
	}
	
	private void bundleStatesToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,State\n");
		
		Bundle[] bundles = bundleTracker.getBundles();
		for(Bundle bundle : bundles) {
			builder.append(createBundleKey(bundle) + CSV_SEPARATOR + stateAsString(bundle) + '\n');
		}
		
		writeFile(DATA_FOLDER + "/bundles-info.csv", builder.toString());
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
			System.out.println("[ADD] " + key + " - STATE: " + stateAsString(bundle));
			return bundle;
		}

		/**
		 * Sets bundle classpath size (in a Resolved state a classloader is 
		 * assigned to a bundle).
		 * Sets ResolvedTime and ResolvingTimeDelta slots in the performance
		 * data structure.
		 */
		public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
			String key = createBundleKey(bundle);

			if(event.getType() == BundleEvent.RESOLVED) {
				//Update number of resolved bundles in data structure.
				resolvedData.put(key, resolvedData.size());

				//Updates classpath data structure.
				try {
					ClassLoader bundleClassLoader = (ClassLoader) getBundleClassLoader(bundle);
					if(bundleClassLoader != null) {
						URL bundleURL = bundleClassLoader.getResource("");
						URL fileURL = FileLocator.toFileURL(bundleURL);
						if(fileURL != null) {
							File root = new File(fileURL.toURI());
							classpathData.put(key, getClassPathSize(root));
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}	
			System.out.println("[MODIFIED] " + key + " - STATE: " + stateAsString(bundle));
		}

		/**
		 * Computes the classpath size of a given folder (e.g. bundle root
		 * folder). Class files are counted.
		 */
		private int getClassPathSize(File folder) {
			int size = 0;

			for(File f : folder.listFiles()) {
				if(f.isFile()) {
					size = (f.getName().endsWith(CLASS_EXTENSION)) ? size + 1 : size;
				}
				else {
					size += getClassPathSize(f);
				}
			}

			return size;
		}

		/**
		 * Returns the classloader of a given bundle.
		 */
		private ClassLoader getBundleClassLoader(Bundle bundle) {
			ClassLoader classloader = null;
			String key = createBundleKey(bundle);

			try {
				String path = System.getProperty("user.dir") + "/plugins/" + key + JAR_EXTENSION;
				InputStream inputStream = new FileInputStream(path);
				JarInputStream jarStream = new JarInputStream(inputStream);
				JarEntry entry = jarStream.getNextJarEntry();

				while(entry != null && classloader == null) {
					if(!entry.isDirectory() && entry.getName().endsWith(CLASS_EXTENSION)) {
						String randomClass = (entry.getName().substring(0,entry.getName().lastIndexOf(CLASS_EXTENSION))).replace("/", ".");
						classloader = bundle.loadClass(randomClass).getClassLoader();
					}
					entry = jarStream.getNextJarEntry();
				}
				return classloader;
			}
			catch(Exception e) {
				//This is a bundle fragment. Returns null.
				return classloader;
			}
		}
	}

}
