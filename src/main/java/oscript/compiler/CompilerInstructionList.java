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


import java.util.*;

// The Bytecode Engineerign Library
import org.apache.bcel.generic.*;


/**
 * These is a rather cheezy implementation... be careful because this
 * doesn't overload all the apend methods.  It is, at least, a way to
 * break some extra crap out of the visitor.
 * 
 * @author Rob Clark (rob@ti.com)
 * <!--$Format: " * @version $Revision$"$-->
 * @version 1.5
 */
public class CompilerInstructionList extends InstructionList
{
  private InstructionHandle lastHandle;
  private LinkedList biList;
  
  private InstructionHandle lastTarget = null;
  
  /*=======================================================================*/
  /**
   * 
   * 
   */
  public CompilerInstructionList()
  {
    super();
  }
  
  
  public InstructionHandle append( Instruction i )
  {
    lastHandle = super.append(i);
    
    if( biList != null )
    {
      for( Iterator itr=biList.iterator(); itr.hasNext(); )
      {
        ((BranchInstruction)(itr.next())).setTarget(lastHandle);
        lastTarget = lastHandle;
        biList = null;
      }
    }
    
    return lastHandle;
  }
  
  public InstructionHandle append( CompoundInstruction i )
  {
    lastHandle = super.append(i);
    
    if( biList != null )
    {
      for( Iterator itr=biList.iterator(); itr.hasNext(); )
      {
        ((BranchInstruction)(itr.next())).setTarget(lastHandle);
        lastTarget = lastHandle;
        biList = null;
      }
    }
    
    return lastHandle;
  }
  
  public BranchHandle append( BranchInstruction i )
  {
    lastHandle = super.append(i);
    
    if( biList != null )
    {
      for( Iterator itr=biList.iterator(); itr.hasNext(); )
      {
        ((BranchInstruction)(itr.next())).setTarget(lastHandle);
        lastTarget = lastHandle;
        biList = null;
      }
    }
    
    return (BranchHandle)lastHandle;
  }
  
  public InstructionHandle getLastInstructionHandle()
  {
    return lastHandle;
  }
  
  public void setNextAsTarget( BranchInstruction bi )
  {
    if( biList == null )
    {
      biList = new LinkedList();
    }
    
    biList.add(bi);
  }
  
  public InstructionHandle getLastBranchTarget()
  {
    return lastTarget;
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

