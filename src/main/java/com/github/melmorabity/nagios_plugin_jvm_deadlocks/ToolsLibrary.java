package com.github.melmorabity.nagios_plugin_jvm_deadlocks;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads the tools.jar library at runtime.
 */
public class ToolsLibrary {
	/**
	 * Potential tools.jar JAR paths.
	 */
	private static enum TOOLS_LIBRARY_PATHS {
		JRE(System.getProperty("java.home") + File.separator + ".." + File.separator + "lib" + File.separator
				+ "tools.jar"), // JRE
		JDK(System.getProperty("java.home") + File.separator + "lib" + File.separator + "tools.jar"); // JDK

		private String path;

		TOOLS_LIBRARY_PATHS(String path) {
			this.path = path;
		}
	}

	/**
	 * Load tools.jar library, required by the plugin, at runtime.
	 * 
	 * @throws NagiosPluginJVMDeadlocksException
	 *             if:
	 *             <ul>
	 *             <li>the library is unavailable</li>
	 *             <li>the library can't be imported</li>
	 *             </ul>
	 */
	public static void loadToolsLibrary() throws NagiosPluginJVMDeadlocksException {
		try {
			/* Check whether the library is already in the classpath */
			Class.forName("com.sun.tools.attach.VirtualMachine.class");
		} catch (ClassNotFoundException e) {
			/* Check whether the library exists */
			File toolsJar = null;
			boolean toolsJarExists = false;
			for (TOOLS_LIBRARY_PATHS tools : TOOLS_LIBRARY_PATHS.values()) {
				toolsJar = new File(tools.path);
				if (toolsJar.exists()) {
					toolsJarExists = true;
					break;
				}
			}
			if (!toolsJarExists) {
				throw new NagiosPluginJVMDeadlocksException("Unable to locate tools.jar");
			}

			URL url;
			try {
				url = toolsJar.toURI().toURL();
				URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
				Class<URLClassLoader> c = URLClassLoader.class;

				/* Use reflection */
				Method method = c.getDeclaredMethod("addURL", new Class[] { URL.class });
				method.setAccessible(true);
				method.invoke(classLoader, new Object[] { url });
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | MalformedURLException e1) {
				throw new NagiosPluginJVMDeadlocksException(e);
			}
		}
	}

}
