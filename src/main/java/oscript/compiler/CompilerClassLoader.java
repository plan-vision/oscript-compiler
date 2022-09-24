/*=============================================================================
 *     Copyright Texas Instruments 2000.  All Rights Reserved.
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * $ProjectHeader: OSCRIPT 0.155 Fri, 20 Dec 2002 18:34:22 -0800 rclark $
 */


package oscript.compiler;


import oscript.OscriptInterpreter;

// The Bytecode Engineerign Library
import org.apache.bcel.classfile.JavaClass;

import java.security.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;


/**
 * A helper to create loaded java classes from a <code>JavaClass</code>
 * instance.  
 * <p>
 * Classes created with this class-loader are also stored to
 * <code>.cache</code> so they will be available to future instances
 * of the interpreter... note: I may change this in the future, I would
 * like to make sure that the classes are stored in the same file as
 * <code>cache.db</code>...
 * 
 * @author Rob Clark (rob@ti.com)
 * <!--$Format: " * @version $Revision$"$-->
 * @version 1.10
 */
public class CompilerClassLoader extends ClassLoader
{
  private static CompilerClassLoader loader;
  //////////////////////////////////////////////////////////////////////////////
  // attempt to see if avoiding Class.forName helps:
  private static java.util.Map classCache = new java.util.WeakHashMap();
  private static ClassNotFoundException CLASS_NOT_FOUND_EXCEPTION =
    new ClassNotFoundException();
  public synchronized static Class forName( String className, boolean initialize)
    throws ClassNotFoundException
  {
    if( loader == null )
      loader = getCompilerClassLoader();
    
    className = className.intern();
    Object obj = classCache.get(className);
    
    if( obj == Boolean.FALSE )
      throw CLASS_NOT_FOUND_EXCEPTION;
    
    Class javaClass = (Class)obj;
    
    if( javaClass == null )
    {
      try
      {
        javaClass = Class.forName( className, initialize, loader );
        classCache.put( className, javaClass );
      }
      catch(ClassNotFoundException e)
      {
        classCache.put( className, Boolean.FALSE );
        throw e;
      }
    }
    
    return javaClass;
  }
  //////////////////////////////////////////////////////////////////////////////
  
  public synchronized static CompilerClassLoader getCompilerClassLoader()
  {
    if( loader == null )
      loader = new CompilerClassLoader();
    return loader;
  }
  
  
  /**
   * Class Constructor, private to enforce singleton pattern
   */
  CompilerClassLoader()
  {
    super( CompilerClassLoader.class.getClassLoader() );
    /*
    Policy.setPolicy( new Policy() {
    
      public PermissionCollection getPermissions( CodeSource cs )
      {
        Permissions p = new Permissions();
        p.add( new AllPermission() );
        return p;
      }
      
      public void refresh()
      {
      }
      
    } );*/
  }
  
  
  /** */
  private static String toFileName( String className )
  {
	  try {
		  MessageDigest digest = MessageDigest.getInstance("SHA-256");
		  byte[] hash = digest.digest(className.getBytes("UTF-8"));
		  String result = new BigInteger(1, hash).toString(16);
		  return result+".class";
	  } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
		  return "ERROR in toFileName OScript!";
	  }
  }
  
  /**
   * In order to ensure that new classes are not generated with the same
   * name as a previously generated class, the process of selecting the
   * the name for the class to generate is delegated to this class-loader.
   * This is a wierd thing for a class loader to do, but maybe I can think
   * of a cleaner way to do this...
   * 
   * @param suggestedClassName is used to seed the class name generation, so
   * that the name that is generated sort of "makes sense"
   */
  public String makeClassName( String suggestedClassName )
  {
    return makeClassName( suggestedClassName, false );
  }
  
  
  private static boolean writeClassInTmp = false; /* TEST ONLY */
  public static final String cachePath="work/tmp/vscript-next"; // TODO OK?

  static 
  {
	  File f = new File(cachePath);	// TODO OK??
	  f.mkdirs();
  }
  
  
  public synchronized String makeClassName( String suggestedClassName, boolean overwrite )
  {
    return suggestedClassName;
  }
  
  /**
   * Make a class... perhaps this method should take a ClassGen???
   */
	public synchronized Class makeClass( String className, JavaClass javaClass )
  {
    Class c = null;
    
    try
    {
      // dump the .class to a byte array, so we can immediately instantiate:
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      javaClass.dump(bos);
      
      byte[] bytes = bos.toByteArray();
      // save to file:
      File file = OscriptInterpreter.resolve(cachePath + "/"+ toFileName(className), false);
      if (writeClassInTmp) {
          FileOutputStream os = new FileOutputStream(file,false);
          os.write(bytes);
          os.flush();
          os.close();
      }
      // load the class from the byte array:
      c = defineClass( className, bytes, 0, bytes.length );
    }
    catch(Exception e)
    {
      // this shouldn't actually happen:
      e.printStackTrace();
    }
    
    return c;
  }
   
   public synchronized Class makeClass( String className, byte[] bytes ) {
	    Class c = null;	    
	    try {
	      c = defineClass( className, bytes, 0, bytes.length );
	    } catch(Exception e) {
	      e.printStackTrace();
	    }
	    return c;
   }

}



/*
 *   Local Variables:
 *   tab-width: 2
 *   indent-tabs-mode: nil
 *   mode: java
 *   c-indentation-style: java
 *   c-basic-offset: 2
 *   eval: (c-set-offset 'substatement-open '0)
 *   End:
 */

