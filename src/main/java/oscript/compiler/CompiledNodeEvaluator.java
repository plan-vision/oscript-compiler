/*=============================================================================
 *     Copyright Texas Instruments 2000-2004.  All Rights Reserved.
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

import java.io.File;
import java.io.UnsupportedEncodingException;

import oscript.data.*;
import oscript.util.*;
import oscript.*;
import oscript.exceptions.PackagedScriptObjectException;
import oscript.parser.ParseException;


/**
 * This is the base class for all compiled script functions.  I could have
 * used an interface, but there was no need for a compiled script function
 * object to subclass any other object.
 * <p>
 * A compiled function does not store the scope the script function was
 * defined in, but simply a compiled version of the function's syntaxtree.
 * This means that once a function is compiled for one scope, it does not
 * need to be recompiled for a different scope.
 * 
 * @author Rob Clark (rob@ti.com)
 * <!--$Format: " * @version $Revision$"$-->
 * @version 1.19
 */
public abstract class CompiledNodeEvaluator
  extends NodeEvaluator
{
  private String name;
  /**
   * if the desc is a file path, then this won't be null... eventually
   * i'd like the desc to be a file, so we won't need this, but that
   * will require parser mods, so for now:
   */
  private File file;
  
//  protected CompiledNodeEvaluator() {}
  
//  protected final static Object _readObject( java.io.ObjectInput in )
//    throws java.io.IOException, ClassNotFoundException
//  {
//    return in.readObject();
//  }
//  
//  protected final static void _writeObject( java.io.ObjectOutput out, Object obj )
//    throws java.io.IOException
//  {
//    out.writeObject(obj);
//  }
  /*=======================================================================*/
  
  /*=======================================================================*/
  /**
   * Class Constructor.
   * 
   * @param name         the name of this function
   * @param desc         the description of input source (ie. filename)
   */
  protected CompiledNodeEvaluator( String name)
  {
    super();
    if (name.startsWith("B62")) 
    {
    	try {
			name=new String(Base62.decode(name.substring(3)),"UTF-8");
		} catch (UnsupportedEncodingException e) {		
			e.printStackTrace();
		}
    	this.file = new File(name);
    } else {
    	this.file = new File(name);
    }
    this.name = name;
  }
  
  /*=======================================================================*/
  /**
   * Get the file that this node was parsed from.
   * 
   * @return the file
   */
  public File getFile()
  {   
    return file;
  }
  
  /*=======================================================================*/
  /**
   * Get the function symbol (name), if this node evaluator is a function, 
   * otherwise return <code>-1</code>.
   * 
   * @return the symbol, or <code>-1</code>
   */
  public int getId()
  {
    /* name is going to be a class name... currently the class name will 
     * either be a function name, or a translated path (for a node that
     * corresponds to a file) in which case it will start with "root.":
     */
    return name.startsWith("root.") ? -1 : Symbol.getSymbol(name).getId();
  }
  
  /*=======================================================================*/
  /**
   * Evaluate, in the specified scope.  If this is a function, the Arguments 
   * to the function, etc., are defined in the <code>scope</code> that the 
   * function is evaluated in.
   * 
   * @param sf           the stack frame to evaluate the node in
   * @param scope        the scope to evaluate the function in
   * @return the result of evaluating the function
   */
  public Object evalNode( StackFrame sf, Scope scope )
    throws PackagedScriptObjectException
  {
    // this node evaluator is always index 0.  For inner-nodes,  a non-zero 
    // index is used, which is passed to the CompiledInnerNodeEvaluator (which
    // passes it back when it calls this object's evalInnerNode())
    return evalInnerNode( 0, sf, scope );
  }
  
  /*=======================================================================*/
  /**
   * Evaluate, in the specified scope.  If this is a function, the Arguments 
   * to the function, etc., are defined in the <code>scope</code> that the 
   * function is evaluated in.
   * 
   * @param sf           the stack frame to evaluate the node in
   * @param scope        the scope to evaluate the function in
   * @return the result of evaluating the function
   */
  public abstract Object evalInnerNode( int idx, StackFrame sf, Scope scope )
    throws PackagedScriptObjectException;
  
  /*=======================================================================*/
  /**
   * Get the SMIT for the scope(s) created when invoking this node evaluator.
   * 
   * @param perm  <code>PRIVATE</code>, <code>PUBPROT</code>,
   *   <code>ALL</code>
   */
  public SymbolTable getSharedMemberIndexTable( int perm )
  {
    // this node evaluator is always index 0.  For inner-nodes,  a non-zero 
    // index is used, which is passed to the CompiledInnerNodeEvaluator (which
    // passes it back when it calls this object's evalInnerNode())
    return getInnerSharedMemberIndexTable( 0, perm );
  }
  
  /*=======================================================================*/
  /**
   * Get the SMIT for the scope(s) created when invoking this node evaluator.
   */
  public abstract SymbolTable getInnerSharedMemberIndexTable( int idx, int perm );
  
  /*=======================================================================*/
  /**
   * A helper function for evaluating an <i>EvalBlock</i>.
   * 
   * @param str          the string to evaluate
   * @param scope        the scope to evaluate in
   * @return the result
   */
  protected static final Value evalHelper( String str, Scope scope )
  {
    try
    {
      return OscriptInterpreter.eval( str, scope );
    }
    catch(ParseException e)
    {
      throw PackagedScriptObjectException.makeExceptionWrapper( new OException( e.getMessage() ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * A helper function for evaluating an <i>ImportBlock</i>.
   * 
   * @param path         the string identifying what to import
   * @param scope        the scope to evaluate in
   * @return the result
   */
  protected static final Value importHelper( String path, Scope scope )
  {
    return OscriptInterpreter.importHelper( path, scope );
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
 *   eval: (c-set-offset 'case-label '+)
 *   eval: (c-set-offset 'inclass '+)
 *   eval: (c-set-offset 'inline-open '0)
 *   End:
 */
