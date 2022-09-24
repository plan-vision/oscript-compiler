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

import oscript.exceptions.*;
import org.apache.bcel.generic.BranchInstruction;
import java.util.*;


/**
 * A stack of <code>LoopStackNode</code> is used to track loop bodies, and
 * related things, such as cleanup instructions that need to be inserted prior
 * to a jump or return out of a loop.
 */
class LoopStackNode
{
  private static final LinkedList EMPTY_LIST = new LinkedList();
  
  private LoopStackNode prev;
  private LinkedList    cleanupList;
  private LinkedList    breakInstructionList;
  private LinkedList    continueInstructionList;
  
  /**
   * Class Constructor.
   */
  LoopStackNode( LoopStackNode prev )
  {
    this.prev = prev;
  }
  
  /**
   * Pop the current loop-stack node off the stack, returning the next in
   * the stack.
   */
  LoopStackNode pop()
  {
    if( (cleanupList != null) && (cleanupList.size() > 0) )
      throw new ProgrammingErrorException("unremoved cleanup instruction generator!");
    return prev;
  }
  
  /**
   * Add a branch instruction used to implement a "break".  The branch target
   * of this instruction will be set appropriately before this loop stack node
   * is popped off the stack.  <!-- currently done by "while" loop visitor -->
   */
  void addBreakBranchInstruction( BranchInstruction bi )
  {
    if( breakInstructionList == null )
      breakInstructionList = new LinkedList();
    breakInstructionList.add(bi);
  }
  
  /**
   * Add a branch instruction used to implement a "continue".  The branch target
   * of this instruction will be set appropriately before this loop stack node
   * is popped off the stack.  <!-- currently done by "while" loop visitor -->
   */
  void addContinueBranchInstruction( BranchInstruction bi )
  {
    if( continueInstructionList == null )
      continueInstructionList = new LinkedList();
    continueInstructionList.add(bi);
  }
  
  /**
   * 
   */
  Collection getContinueInstructions()
  {
    if( continueInstructionList == null )
      return EMPTY_LIST;
    return continueInstructionList;
  }
  
  /**
   * 
   */
  Collection getBreakInstructions()
  {
    if( breakInstructionList == null )
      return EMPTY_LIST;
    return breakInstructionList;
  }
  
  /**
   * Add a clean-up instruction generator that will be called in the case of
   * a jump/return out of this loop body.  The instruction generator should
   * be removed after visiting the children of the syntax-tree node visit
   * that added the generator.  The instruction generator should not itself
   * add/remove instruction generators, and it should not cause a net change
   * to the stack height.  Note that the instruction generator may be called
   * multiple times if there are multiple points of jump/return out the loop
   * body.
   */
  void addCleanupInstructionGenerator( CleanupInstructionGenerator g )
  {
    if( cleanupList == null )
      cleanupList = new LinkedList();
    cleanupList.addFirst(g);  // XXX I think we want to run most recently added generator first?
  }
  
  /**
   * Remove a clean-up instruction generator.
   */
  void removeCleanupInstructionGenerator( CleanupInstructionGenerator g )
  {
    cleanupList.remove(g);
  }
  
  /**
   * Called in the case of a jump/return out of this loop body.
   * 
   * @param il    the instruction list to insert instructions into
   * @param all   <code>true</code> if "return" type exception that exits all
   *   loop frames, or <code>false</code> if "break" or "continue" type jump
   *   out of just this loop frame.
   */
  void insertCleanupInstructions( CompilerInstructionList il, boolean all )
  {
    if( cleanupList != null )
      for( Iterator itr=cleanupList.iterator(); itr.hasNext(); )
        ((CleanupInstructionGenerator)(itr.next())).generate(il);
    if( all && (prev != null) )
      prev.insertCleanupInstructions( il, all );
  }
  
  /**
   * A cleanup-instruction-generator is a generic interface that other parts
   * of the compiler can use if they need to generate cleanpup instructions
   * before a jump out of a loop body.  This gives other parts of the compiler 
   * that need to generate instructions to perform cleanup (such as running 
   * "finally"s or releasing monitors) a chance to do that.
   */
  public interface CleanupInstructionGenerator
  {
    /**
     * Called when there is a jump out of the loop body (as opposed to normal
     * completion of loop).
     * 
     * @param il   the instruction list to insert instructions into
     */
    public void generate( CompilerInstructionList il );
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

