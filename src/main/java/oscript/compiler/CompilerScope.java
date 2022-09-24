/*=============================================================================
 *     Copyright Texas Instruments 2000-2003.  All Rights Reserved.
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

import oscript.data.*;
import oscript.syntaxtree.*;
import oscript.util.*;


// The Bytecode Engineerign Library
import org.apache.bcel.generic.*;



/**
 * This class helps the compiler track declarations of variables within a
 * scope, in order to optimize resolving references to variables by caching
 * and, when possible, statically resolving the reference in order to avoid
 * performing the normal hashtable lookup(s).  It is also responsible for
 * generating the {@link Scope#createMember} and {@link Scope#lookupInScope}
 * code, so that the rest of the compiler does not need to worry about what
 * optimization strategy (if any) is used to resolve references.
 * <p>
 * Note that this class is fairly tightly coupled to {@link CompilerVisitor}.
 * It would be an inner class if it were not for file size issues.
 * 
 * @author Rob Clark (rob@ti.com)
 * @version 0
 */
public class CompilerScope
{
  private static final org.apache.bcel.generic.Type BASIC_SCOPE_TYPE  = new ObjectType("oscript.data.BasicScope");
  
  private SymbolTable smit = new OpenHashSymbolTable();
  private SymbolTable privSmit;
  private SymbolTable pubpSmit;
  
  private int slot; 
  private LocalVariableGen lg;
  private CompilerScope prev;
  private boolean hasFxnInScope;
  private Hashtable memberTable = new Hashtable();
  private int conditionalNestingCnt = 0;
  private int smitIdx = -1;
  private int privSmitIdx, pubpSmitIdx;
  
  /**
   * The compiler for the compiled unit within which this scope is 
   * declared.
   */
  CompilerVisitor cv;
  
  /**
   * Used to mark that the scope is open, meaning there may be variables
   * declared in this scope that the compiler doesn't know about.  This
   * can be the result of an "eval", "import", or "mixin".
   */
  private boolean openScope = false;
  
  
  /**
   * Constructor for scope to represent a scope passed in to the
   * node-evaluator, rather than constructed by the node-evaluator
   * 
   * @param cv       the compiler that declares this scope
   * @param slot     the local variable slot of the externally constructed
   *    scope object
   * @param argIds   array of argument ids and attributes
   */
  CompilerScope( CompilerVisitor cv, int slot, int[] argIds )
  {
    this.cv   = cv;
    this.slot = slot;
    
    // for top-level scopes, also construct private and public/protect
    // index tables:
    privSmit = new OpenHashSymbolTable();
    pubpSmit = new OpenHashSymbolTable();
    
    if( argIds != null )
      for( int i=0; i<argIds.length; i+=2 )
        smit.create( argIds[i] );
    
    // create smit instance variable:
    smitIdx     = cv.ctx.getInstanceConstantIdx(smit);
    privSmitIdx = cv.ctx.getInstanceConstantIdx(privSmit);
    pubpSmitIdx = cv.ctx.getInstanceConstantIdx(pubpSmit);
  }
  
  /**
   * Constructor to represent a scope constructed by this node-evaluator.
   * 
   * @param cv       the compiler that declares this scope
   * @param prev     the enclosing scope
   * @param hasFxnInScope   a flag from the parser indicating if there is
   *    (or might be) a function declaration within this scope.  If there
   *    is no function in this scope, scope storage can be allocated from
   *    the stack
   */
  CompilerScope( CompilerVisitor cv, CompilerScope prev, boolean hasFxnInScope )
  {
    this.cv = cv;
    this.prev = prev;
    this.hasFxnInScope = hasFxnInScope;
    
    // create local variable for scope object:
    lg = cv.mg.addLocalVariable( CompilerContext.makeUniqueIdentifierName("scope"),
                                 BASIC_SCOPE_TYPE,
                                 null,
                                 null );
    slot = lg.getIndex();
    
    // create smit instance variable:
    smitIdx = cv.ctx.getInstanceConstantIdx(smit);
    
    // if no fxn in scope, we can re-use the scope, which entails
    // extra logic to see if a scope has already been created, 
    // and to reset() when leaving the scope... also, we can allocate
    // the scope from the stack:
    if(!hasFxnInScope)
    {
      BranchInstruction IFNONNULL = null;
      // note insert into head of list, hence insert'd instructions are
      // in reverse order:
      cv.il.insert( new ASTORE(slot) );
      cv.il.insert( InstructionConst.ACONST_NULL );
      cv.il.append( new ALOAD(slot) );
      IFNONNULL = new IFNONNULL(null);
      cv.il.append(IFNONNULL);
      
      // "sf.allocateScope(currentScope,smit)"
      cv.il.append( InstructionConst.ALOAD_1 );  // sf
      cv.il.append( new ALOAD( prev.getSlot() ) );   // currentScope
      cv.il.append( new GETSTATIC(smitIdx) );        // smit
      cv.il.append( 
        new INVOKEVIRTUAL( 
          cv.ctx.methodref( 
            "oscript.util.StackFrame",
            "allocateBasicScope",
            "(Loscript/data/Scope;Loscript/util/SymbolTable;)Loscript/data/BasicScope;" 
          )
        )
      );
      
      // "scopeXX = " ...
      cv.il.append( new ASTORE(slot) );
      
      if(!hasFxnInScope)
        cv.il.setNextAsTarget(IFNONNULL);
    }
    else
    {
      // "new BasicScope(currentScope,smit)"
      cv.il.append( new NEW( cv.ctx.cp.addClass("oscript.data.BasicScope") ) );
      cv.il.append( InstructionConst.DUP );
      cv.il.append( new ALOAD( prev.getSlot() ) );   // currentScope
      cv.il.append( new GETSTATIC(smitIdx) );        // smit
      cv.il.append( 
        new INVOKESPECIAL( 
          cv.ctx.methodref( 
            "oscript.data.BasicScope",
            "<init>",
            "(Loscript/data/Scope;Loscript/util/SymbolTable;)V" 
          )
        )
      );
      
      // "scopeXX = " ...
      cv.il.append( new ASTORE(slot) );
    }
  }
  
  /**
   * Get the instance-constant indexs of the SMIT... this is needed for the
   * topmost CompilerScope in a function, so that the compiler can generate
   * an accessor method.
   */
  int[] getSharedMemberIndexTableIdxs()
  {
    return new int[] { smitIdx, pubpSmitIdx, privSmitIdx };
  }
  
  /**
   * Mark this scope as open.  An open scope may possibly have members
   * declared in it that the compiler does not know about, such as 
   * because of an <code>eval</code> or <code>import</code> statement.
   */
  void markOpen()
  {
    openScope = true;
    
    // XXX should clear any cached lookups, since the lookup may
    //     change after this point
  }
  
  /**
   * Called when processing has reached the end of this scope, to pop
   * this scope of the stack
   */
  CompilerScope pop()
  {
    if( prev != null )
    {
      if(!hasFxnInScope)
      {
        cv.il.append( new ALOAD(slot) );
        cv.il.append( new INVOKEVIRTUAL( cv.ctx.methodref(
          "oscript.data.BasicScope",
          "reset",
          "()V"
        ) ) );
      }
    }
    lg.setEnd( cv.il.getLastInstructionHandle() );
    return prev;
  }
  
  /**
   * Get the slot for the local variable that refers to this scope object
   */
  int getSlot()
  {
    return slot;
  }
  
  /**
   * Generate the {@link Scope#createMember} call.  The code is generated
   * at the current position in the compiler that this scope is declared
   * in.  Stack:
   * <pre>
   *   ... -&gt; ... , Value
   * </pre>
   * 
   * @param id   the &lt;IDENTIFIER&gt; token
   * @param attr         the permissions attribute
   */
  void createMember( NodeToken id, int attr )
  {
    cv.il.append( new ALOAD( getSlot() ) );
    cv.handle(id);
    cv.ctx.pushSymbol( cv.il, id.tokenImage );
    cv.ctx.pushInt( cv.il, attr );
    cv.il.append( new INVOKEVIRTUAL( cv.ctx.methodref( "oscript.data.Scope",
                                                       "createMember",
                                                       "(II)Loscript/data/Value;" ) ) );
    
    createMemberImpl( id.otokenImage ).dumpInitializer();
    
    int iid = Symbol.getSymbol( id.otokenImage ).getId();
    smit.create(iid);
    if( privSmit != null )
    {
      if( attr == Reference.ATTR_PRIVATE )
        privSmit.create(iid);
      else
        pubpSmit.create(iid);
    }
  }
  
  private Member createMemberImpl( Value name )
  {
    Member member = new Member( this, name );
    memberTable.put( name, member );
    return member;
  }
  
  /**
   * Dump the code to perform a {@link Scope#lookupInScope}.
   * 
   * @param cv   the compiler instance within which to perform the lookup
   * @param id   the &lt;IDENTIFIER&gt; token
   */
  void lookupInScope( CompilerVisitor cv, NodeToken id )
  {
    cv.handle(id);
    
    Value    name = id.otokenImage;
    Member member = (Member)(memberTable.get(name));
    
    if( member == null )
    {
      if( (prev == null) || openScope )
        createMemberImpl(name).dumpLookup(cv);
      else
        prev.lookupInScope( cv, id );
    }
    else
    {
      member.dumpLookup(cv);
    }
  }
  
  /**
   * Called by the compiler to indicate that compilation has entered a 
   * potentially condional path within this scope.  (It does not matter 
   * if this scope is entirely enclosed by a conditional path, what does 
   * matter is a conditional path enclosed by this scope.)
   * 
   * @see #leaveConditional
   */
  void enterConditional() { conditionalNestingCnt++; }
  
  /**
   * Called by the compiler to indicate that compilation has left a
   * potentially conditional path within this scope.
   * 
   * @see #enterConditional
   */
  void leaveConditional() { conditionalNestingCnt--; }
  
  /**
   */
  boolean inConditional()
  {
    return cv.scope.inConditionalImpl(this);
  }
  
  private boolean inConditionalImpl( CompilerScope terminator )
  {
    if( conditionalNestingCnt > 0 )
      return true;
    else if( this != terminator )
      return prev.inConditionalImpl(terminator);
    else
      return false;
  }
}


/**
 * An instance of this class is created for each variable that is declared
 * or looked up, to track the status of that variable
 */
class Member
{
  private int slot = -1;
  private CompilerScope scope;
  private Value name;
  private boolean mayNeedToLoad;
  private boolean definitelyNeedToLoad = true;
  private CompilerVisitor cv;
  
  private InstructionHandle initializerHandle = null;
  
  /**
   * Class Constructor for a member of a scope.
   * 
   * @param scope   the scope this member is declared in
   * @param name    the member name
   */
  Member( CompilerScope scope, Value name )
  {
    this.scope = scope;
    this.name  = name;
    cv = scope.cv;
    
    mayNeedToLoad = scope.inConditional();
    
    cv.defer( new Runnable() {
      public void run()
      {
        if( initializerHandle != null )
        {
          try {
            cv.il.delete(initializerHandle);
          } catch(Throwable t) {
            Thread.dumpStack();
          }
          initializerHandle = null;
        }
      }
    } );
  }
  
//  protected void finalize()
//  {
//    System.err.println(name + ": slot=" + slot);
//  }
  
  /**
   * Called when we decide that this member should be cached.
   */
  private void createLocalVar()
  {
    if( slot != -1 )
      return;            // already created
    
    LocalVariableGen lg = cv.mg.addLocalVariable( 
      CompilerContext.makeUniqueIdentifierName( name.castToString() ),
      CompilerContext.VALUE_TYPE,
      null,          // XXX setStart!
      null
    );
    
    slot = lg.getIndex();
    
    //if(XXX)
    {
      cv.il.insert( new ASTORE(slot) );  // insert at head in reverse order
      cv.il.insert( InstructionConst.ACONST_NULL );
    }
    
if( initializerHandle == null ) Thread.dumpStack();
    
    // replace initializerHandle:
    InstructionHandle tmp = cv.il.append( initializerHandle, InstructionConst.DUP );
    cv.il.append( tmp, new ASTORE(slot) );
  }
  
  /**
   * can only be called by defining scope, after createMember
   */
  void dumpInitializer()
  {
if( initializerHandle != null ) Thread.dumpStack();
    initializerHandle = cv.il.append( InstructionConst.NOP );
    definitelyNeedToLoad = false;
  }
  
  /**
   * can only be called by defining scope, after lookupInScope
   */
  void dumpLookup( CompilerVisitor cv )
  {
    // XXX perhaps this could be cleaned up:
    if(definitelyNeedToLoad)
    {
      mayNeedToLoad = scope.inConditional();
      
      cv.il.append( new ALOAD( scope.getSlot() ) );
      
      cv.ctx.pushSymbol( cv.il, name.castToString() );
      
      cv.il.append( new INVOKEVIRTUAL( cv.ctx.methodref( 
        "oscript.data.Scope",
        "lookupInScope",
        "(I)Loscript/data/Value;"
      ) ) );
      
      dumpInitializer();
    }
    else if(mayNeedToLoad)
    {
      createLocalVar();
      
      mayNeedToLoad = scope.inConditional();
      
      cv.il.append( new ALOAD(slot) );
      
      cv.il.append( InstructionConst.DUP );
      
      BranchInstruction IFNONNULL = new IFNONNULL(null);
      cv.il.append(IFNONNULL);
      cv.il.append( InstructionConst.POP );
      cv.il.append( new ALOAD( scope.getSlot() ) );
      
      cv.ctx.pushSymbol( cv.il, name.castToString() );
      
      cv.il.append( new INVOKEVIRTUAL( cv.ctx.methodref( 
        "oscript.data.Scope",
        "lookupInScope",
        "(I)Loscript/data/Value;" 
      ) ) );
      cv.il.append( InstructionConst.DUP );
      cv.il.append( new ASTORE(slot) );
      
      cv.il.setNextAsTarget(IFNONNULL);
    }
    else
    {
      createLocalVar();
      cv.il.append( new ALOAD(slot) );
    }
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

