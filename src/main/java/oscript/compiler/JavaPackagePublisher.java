package oscript.compiler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oscript.OscriptHost;
import oscript.OscriptInterpreter;
import oscript.data.JavaClassWrapper;
import oscript.data.Reference;
import oscript.data.Scope;
import oscript.data.Symbol;
import oscript.data.Value;
import oscript.exceptions.PackagedScriptObjectException;

public final class JavaPackagePublisher {

	private static final class ChildMapResolve extends Value {
		public final Map<String,Value> children;
		public ChildMapResolve() {
			this.children=new HashMap();
		}

		@Override
		public Value getMember(int id, boolean exception) throws PackagedScriptObjectException {
			Value v = children.get(Symbol.getSymbol(id).castToString());
			if (v != null)
				return v;
			return super.getMember(id, exception);
		}
		@Override
		public Value elementAt(Value idx) throws PackagedScriptObjectException {
			Value v = children.get(idx.castToString());
			if (v != null)
				return v;
			return super.elementAt(idx);
		}
		
		@Override
		protected Value getTypeImpl() {
			return null;
		}
	}
	
	
	public static void publishJavaPackage(String packageName) {
		try {
			Class classes[] = getClasses(packageName);
			String pfx = packageName+".";
			for (Class c : classes) {
				if (!c.getName().startsWith(pfx))
					OscriptHost.me.error("Wrong class "+c.getName()+" for package "+packageName);
				else {
					String arr[] = c.getName().substring(pfx.length()).split("\\.");
					Scope scope = OscriptInterpreter.getGlobalScope();
					Value t = scope.getMember("java",false);
					ChildMapResolve r = null;
					if (t == null) {
						r = new ChildMapResolve();
						scope.createMember("java",Reference.ATTR_CONST | Reference.ATTR_PUBLIC).opAssign(r); 
					} else 
						r = (ChildMapResolve)t.unhand();
					//-------------------------------------------
					for (int i=0;i<arr.length-1;i++) {
						t = r.getMember(arr[i],false);
						ChildMapResolve n = null;
						if (t == null) {
							n = new ChildMapResolve();
							r.children.put(arr[i], n);
						} else 
							n = (ChildMapResolve)t.unhand();
						r=n;
					}
					JavaClassWrapper jcv = JavaClassWrapper.getClassWrapper(c);
					jcv.init();
					r.children.put(arr[arr.length-1],jcv);					
				}
			}
			
		} catch (ClassNotFoundException | IOException e) {
			// TODO Auto-generated catch block
			OscriptHost.me.error(e+"");
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
	    while (resources.hasMoreElements()) {
	        URL resource = resources.nextElement();
	        dirs.add(new File(resource.getFile()));
	    }
	    ArrayList<Class> classes = new ArrayList<Class>();
	    for (File directory : dirs) {
	        classes.addAll(findClasses(directory, packageName));
	    }
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
	private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {
	    List<Class> classes = new ArrayList<Class>();
	    if (!directory.exists()) {
	        return classes;
	    }
	    File[] files = directory.listFiles();
	    for (File file : files) {
	    	String name = file.getName();
	        if (file.isDirectory()) {
	            classes.addAll(findClasses(file, packageName + "." + name));
	        } else if (name.endsWith(".class") && name.indexOf("$") < 0) {
	            classes.add(Class.forName(packageName + '.' + name.substring(0, name.length() - 6)));
	        }
	    }
	    return classes;
	}
	//-----------------------------------------------------------------------------
	public static void publishJavaPackages() {
		publishJavaPackage("com.planvision.visionr.core.scripting.oscript.api");
	}	    
}
