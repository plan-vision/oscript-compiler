package oscript.compiler;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;

import oscript.OscriptHost;

public final class JavaPackageReader 
{
	public static Class[] getPackageClasses(String packageName) 
	{
		try 
		{
			return getClasses(packageName);
		} catch (ClassNotFoundException | IOException e) {
			OscriptHost.me.error(e+"");
			return new Class[0];
		} 
	}

	/**
	 * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
	 *
	 * @param packageName The base package
	 * @return The classes
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	private static Class[] getClasses(String packageName)
	        throws ClassNotFoundException, IOException {
		
	    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	    String path = packageName.replace('.', '/');
	    Enumeration<URL> resources = classLoader.getResources(path);
	    List<File> dirs = new ArrayList<File>();
	    List<URL> jars = new ArrayList<URL>();
	    while (resources.hasMoreElements()) {
	        URL resource = resources.nextElement();
	        if (resource.getProtocol().equals("jar")) {
	        	jars.add(resource);
	        } else {
		        dirs.add(new File(resource.getFile()));
	        }
	    }
	    ArrayList<Class> classes = new ArrayList<Class>();
	    HashSet<String> used = new HashSet();
	    // SCAN JARS 
	    for (URL jar : jars) {
	    	 JarURLConnection jarConnection = (JarURLConnection)jar.openConnection();
	    	 //jarConnection.connect();
	    	 String pp = path+"/";
	    	 for (JarEntry e : Collections.list(jarConnection.getJarFile().entries())) {
	    		 String name = e.getName();
	    		 if (name.endsWith(".class") && name.startsWith(pp) && !name.contains("$")) {
	    			 String cname = name.substring(0, name.length() - 6).replace('/', '.');
	    			 if (used.add(cname)) 
		    			 classes.add(Class.forName(cname));
	    		 }
	    	 }

	    }
	    // SCAN DIRS
	    for (File directory : dirs) 
	        classes.addAll(findClasses(directory, packageName,used));
	    return classes.toArray(new Class[classes.size()]);
	}

	/**
	 * Recursive method used to find all classes in a given directory and subdirs.
	 *
	 * @param directory   The base directory
	 * @param packageName The package name for classes found inside the base directory
	 * @return The classes
	 * @throws ClassNotFoundException
	 */
	private static List<Class> findClasses(File directory, String packageName,HashSet<String> used) throws ClassNotFoundException {
	    List<Class> classes = new ArrayList<Class>();
	    if (!directory.exists()) {
	        return classes;
	    }
	    File[] files = directory.listFiles();
	    for (File file : files) {
	    	String name = file.getName();
	        if (file.isDirectory()) {
	            classes.addAll(findClasses(file, packageName + "." + name,used));
	        } else if (name.endsWith(".class") && name.indexOf("$") < 0) {
	        	String cname = packageName + '.' + name.substring(0, name.length() - 6);
	        	if (used.add(cname)) {
	        		try {
		        		Class c = Class.forName(cname);
		        		classes.add(c);
	        		} catch (Throwable e) {}
	        	}
	        }
	    }
	    return classes;
	}

}
