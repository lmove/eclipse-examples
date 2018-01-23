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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
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
	private static Map<String,Integer> classpathDependenciesData;
	private static List<String[]> wiringsData;
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
			classpathDependenciesToCSV();
			wiringsToCSV();
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
	private void initializeData() {
		classpathData = new HashMap<String,Integer>();
		classpathDependenciesData = new HashMap<String,Integer>();
		resolvedData = new HashMap<String,Integer>();
		wiringsData = new ArrayList<String[]>();
	}

	/**
	 * Initializes the bundle states map.
	 */
	private void initializeBundleStates() {
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
	private void initializeBundleEventStates() {
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
	private String stateAsString(Bundle bundle) {
		return (bundle == null) ? "NULL" : 
			(bundleStates.containsKey(bundle.getState())) ? bundleStates.get(bundle.getState()) :
				"UNDEFINED";
	}
	
	/**
	 * Returns a string representing the state of the bundle event.
	 */
	private String typeAsString(BundleEvent event) {
		return (event == null) ? "NULL" : 
			(bundleEventStates.containsKey(event.getType())) ? bundleEventStates.get(event.getType()) :
				"UNDEFINED";
	}

	/**
	 * Updates the classpath dfata structures:
	 * - classpathData: considers only the bundle classpath size
	 * - classpathDependenciesData: considers both bundle + dependencies
	 *   classpath sizes.
	 * - wiringsData: considers both import-package and require-bundle
	 *   dependencies.
	 */
	protected void updateClasspathWiringsData(Bundle bundle) {
		try {
			String key = createBundleKey(bundle);
			BundleWiring wiring = bundle.adapt(BundleWiring.class);

			// Logging required packages
			for (BundleWire wire : wiring.getRequiredWires("osgi.wiring.package")) {
				String pkg = (String) wire.getCapability().getAttributes().get("osgi.wiring.package");
				Bundle b = wire.getProviderWiring().getBundle();
				wiringsData.add(new String[] { key, "osgi.wiring.package", createBundleKey(b), pkg});
			}

			// Logging required bundles
			for (BundleWire wire : wiring.getRequiredWires("osgi.wiring.bundle")) {
				Bundle b = wire.getProviderWiring().getBundle();
				wiringsData.add(new String[] { key, "osgi.wiring.bundle", createBundleKey(b), ""});
			}

			// (incudes local classpath + JARs + fragments + dependencies)
			Collection<String> recursiveRes = wiring.listResources("/", "*.class",
					BundleWiring.LISTRESOURCES_RECURSE);

			// (incudes local classpath + JARs + fragments)
			Collection<String> localRes = wiring.listResources("/", "*.class",
					BundleWiring.LISTRESOURCES_LOCAL | BundleWiring.LISTRESOURCES_RECURSE);

			classpathData.put(key, localRes.size());
			classpathDependenciesData.put(key, recursiveRes.size());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * Gets the classpath size of a bundle given it classloader.
	 * Note 1: both with bundle.getResource("") and classloader.getResource("")
	 * we obtain the same results.
	 * Note 2: getResource() is preferred over getEntry(), since the 
	 * first one refers to the classpath size.
	 */
	private int getBundleClassPathSize(Bundle bundle) {
		try {
			URL bundleURL = bundle.getResource("");
			URL fileURL = FileLocator.toFileURL(bundleURL);
			if(fileURL != null) {
				File root = new File(fileURL.toURI());
				return getClassPathSize(root);
			}
			else {
				return 0;
			}
		} 
		catch (IOException | URISyntaxException e) {
			return 0;
		}
	}

	/**
	 * Computes the classpath size of a given folder (e.g. bundle root
	 * folder). Class files are counted.
	 */
	private int getClassPathSize(File folder) {
		int size = 0;
		for(File f : folder.listFiles()) {
			if(f.isFile()) {
				if (f.getName().endsWith(CLASS_EXTENSION)) {
					size += 1;
				} else if (f.getName().endsWith(JAR_EXTENSION)) {
					size += + getJarClassPathSize(f);
				}
			}
			else {
				size += getClassPathSize(f);
			}
		}
		return size;
	}

	/**
	 * Creates a CSV file with the classpath size of resolved bundles.
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
	 * Creates a CSV file with the classpath size of resolved
	 * and non-fragment bundles. Dependencies are included.
	 */
	private void classpathDependenciesToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,Classpath Size\n");

		Set<Entry<String,Integer>> classpaths = classpathDependenciesData.entrySet();
		Iterator<Entry<String,Integer>> it = classpaths.iterator();
		Entry<String,Integer> entry = null;

		while(it.hasNext()) {
			entry = it.next();
			builder.append(entry.getKey() + CSV_SEPARATOR + entry.getValue() + '\n');
		}

		writeFile(DATA_FOLDER + "/classpath-dependencies-info.csv", builder.toString());
	}

	/**
	 * Creates a CSV file with the wirings of resolved bundles.
	 */
	private void wiringsToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,Dependency Type,Wired Bundle,Package\n");

		for(String[] values : wiringsData) {
			String line = "";
			for(int i = 0; i < values.length; i++) {
				line += (i == values.length - 1) ? values[i] : values[i] + CSV_SEPARATOR;
			}
			builder.append(line + '\n');
		}

		writeFile(DATA_FOLDER + "/wirings-info.csv", builder.toString());
	}

	/**
	 * Creates a CSV file with the ordering in which bundles
	 * are resolved.
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

	/**
	 * Creates a CSV file with the final bundles state. 
	 */
	private void bundleStatesToCSV() {
		StringBuilder builder = new StringBuilder();
		builder.append("Bundle,State\n");

		Bundle[] bundles = bundleTracker.getBundles();
		for(Bundle bundle : bundles) {
			builder.append(createBundleKey(bundle) + CSV_SEPARATOR + stateAsString(bundle) + '\n');
		}

		writeFile(DATA_FOLDER + "/bundles-info.csv", builder.toString());
	}

	/**
	 * (WARNING: non-recursive -- yet) Counts the number of classes in a given JAR
	 */
	protected int getJarClassPathSize(File jar) {
		int size = 0;

		try {
			InputStream inputStream = new FileInputStream(jar);
			JarInputStream jarStream = new JarInputStream(inputStream);
			JarEntry entry = jarStream.getNextJarEntry();

			while (entry != null) {
				if (entry.getName().endsWith(CLASS_EXTENSION))
					size += 1;
				entry = jarStream.getNextJarEntry();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		return size;
	}

	/**
	 * Writes a file given a target path and a content.
	 * Parent folders are created.
	 */
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
	protected String createBundleKey(Bundle bundle) {
		return bundle.getSymbolicName() + "_" + bundle.getVersion();
	}


	//------------------------------------------------------------
	// Nested Class
	//------------------------------------------------------------

	private final class OSGiBundleTracker extends BundleTracker {

		//------------------------------------------------------------
		// Methods
		//------------------------------------------------------------

		public OSGiBundleTracker(BundleContext context, int stateMask, BundleTrackerCustomizer customizer) {
			super(context, stateMask, customizer);
		}

		/**
		 * Adds a bundle to the tracker.
		 */
		public Object addingBundle(Bundle bundle, BundleEvent event) {
			String key = createBundleKey(bundle);
//			System.out.println("[ADD] " + key + " - STATE: " + stateAsString(bundle));
			return bundle;
		}

		/**
		 * Sets bundle resolved order.
		 * Sets bundle classpath size (in a Resolved state a classloader is 
		 * assigned to a bundle).
		 */
		public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
			String key = createBundleKey(bundle);

			if(event.getType() == BundleEvent.RESOLVED) {
				//Update number of resolved bundles in data structure.
				resolvedData.put(key, resolvedData.size());
				updateClasspathWiringsData(bundle);
			}	
//			System.out.println("[MODIFIED] " + key + " - STATE: " + stateAsString(bundle));
		}
	}

}
