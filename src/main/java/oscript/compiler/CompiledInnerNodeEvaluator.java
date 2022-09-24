/*=============================================================================
 *     Copyright Texas Instruments 2004.  All Rights Reserved.
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
 */


package oscript.compiler;

import java.io.File;

import oscript.data.*;
import oscript.util.*;
import oscript.*;
import oscript.exceptions.PackagedScriptObjectException;


/**
 * In an effort to generate fewer classes, and improve startup performance,
 * functions within the file/function node that is passed to the compiler
 * generate additional <tt>evalNodeX</tt> methods within the same class that
 * is generated for the parent.  But since we still need individual 
 * {@link NodeEvaluator}s for each function, this class acts as a lightweight
 * wrapper object which forwards the {@link #evalNode} method to the appropriate
 * {@link CompiledNodeEvaluator#evalInnerNode}
 * 
 * @author Rob Clark (rob@ti.com)
 * @version 1
 */
public class CompiledInnerNodeEvaluator
  extends NodeEvaluator
{
  private int id;
  private int idx;
  private CompiledNodeEvaluator cne;
  
  /*=======================================================================*/
  /**
   * Class Constructor.
   * 
   * @param id      the function name symbol id
   * @param idx     the index to pass back to {@link CompiledNodeEvaluator#evalInnerNode}
   * @param cne     the compiled node which contains the compiled code
   */
  public CompiledInnerNodeEvaluator( int id, int idx, CompiledNodeEvaluator cne )
  {
    super();
    
    this.id  = id;
    this.idx = idx;
    this.cne = cne;
  }
  
  /*=======================================================================*/
  /**
   * Get the file that this node was parsed from.
   * 
   * @return the file
   */
  public File getFile()
  {
    // the file is always the same as the parent
    return cne.getFile();
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
    return id;
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
    return cne.evalInnerNode( idx, sf, scope );
  }
  
  /*=======================================================================*/
  /**
   * Get the SMIT for the scope(s) created when invoking this node evaluator.
   * 
   * @param perm  <code>PRIVATE</code>, <code>PUBPROT</code>,
   *   <code>ALL</code>
   */
  public SymbolTable getSharedMemberIndexTable( int perm )
  {
    return cne.getInnerSharedMemberIndexTable( idx, perm );
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
