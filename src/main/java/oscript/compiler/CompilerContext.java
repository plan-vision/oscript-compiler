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


import oscript.syntaxtree.*;
import oscript.OscriptHost;
import oscript.data.*;
import oscript.exceptions.*;
import oscript.util.OpenHashSymbolTable;

// The Bytecode Engineerign Library
import org.apache.bcel.generic.*;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.JavaClass;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Iterator;


/**
 * The syntax-tree (parse-tree) does not simply translate into a single 
 * <tt>NodeEvaluator</tt>.  Instead, functions defined within the file,
 * etc., also turn into "nested" <tt>NodeEvaluator</tt>s.  Each nested 
 * <tt>NodeEvaluator</tt> results in construction of a new compiler
 * (<tt>CompilerVisitor</tt>), which contributes an additional 
 * <tt>evalNode<i>X</i></tt> to the same classfile......
 */
public class CompilerContext
{
  public static int failed = 0;
  public static int succeeded = 0;
  
  /**
   * The index of the last <tt>evalNode<i>X</i>()</tt> method.
   */
  private int lastEvalNodeIdx = -1;
  
  /**
   * The names of the eval-node methods, indexed by eval-node-idx
   */
  private LinkedList evalNodeNameList = new LinkedList();
  
  /**
   */
  String name;
  
  /**
   * The name of the class that is generated.
   */
  String className;
  
  /**
   * The class that is being built.
   */
  ClassGen cg;
  
  /**
   * The constant-pool of the class that is being built.
   */
  ConstantPoolGen cp;
  
  /**
   * topmost compiler instance
   */
  CompilerVisitor cv;
  
  /**
   */
  private Hashtable smitIdxTable = new Hashtable();
  
  /**
   * Maps constants (see <code>NodeToken</code> visit method) to a
   * fieldref.
   */
  private Hashtable constFieldRefTable = new Hashtable();
  
  /**
   * Maps symbol name to a fieldref.
   */
  private Hashtable symbolFieldRefTable = new Hashtable();
  
  /*=======================================================================*/
  /**
   * Note, conflict between org.apache.bcel.generic.Type and oscript.data.Type.
   */
  static final org.apache.bcel.generic.Type OBJECT_TYPE = new ObjectType("java.lang.Object");
  static final org.apache.bcel.generic.Type STRING_TYPE = new ObjectType("java.lang.String");
  static final org.apache.bcel.generic.Type SCOPE_TYPE  = new ObjectType("oscript.data.Scope");
  static final org.apache.bcel.generic.Type VALUE_TYPE  = new ObjectType("oscript.data.Value");
  static final org.apache.bcel.generic.Type SYMBOL_TABLE_TYPE = new ObjectType("oscript.util.SymbolTable");
  static final org.apache.bcel.generic.Type VALUE_ARRAY_TYPE  = new ArrayType(VALUE_TYPE,1);
  static final ObjectType ANY_EXCEPTION_TYPE = new ObjectType("java.lang.Throwable");
  static final ObjectType EXCEPTION_TYPE = new ObjectType("oscript.exceptions.PackagedScriptObjectException");
  
  static final org.apache.bcel.generic.Type[] EVAL_NODE_ARG_TYPES = new org.apache.bcel.generic.Type[] { new ObjectType("oscript.util.StackFrame"), SCOPE_TYPE };
  static final String[]                       EVAL_NODE_ARG_NAMES = new String[] { "sf", "scope"};
  
  public static final int[] EMPTY_ARG_IDS  = new int[0];
  
  /**
   * The instructions for the "<clinit>" method.
   */
  private InstructionList clinitIl = new CompilerInstructionList();
  
  /**
   * The instructions for the "<init>" method.
   */
  private InstructionList initIl = new CompilerInstructionList();
  
  /*=======================================================================*/
  /**
   * The entry-point to the compiler
   */
  public static final byte[] compileNode( String name, Node node )
  {
    synchronized(ClassGen.class)
    {
      return (new CompilerContext(name)).compileNodeImpl(node);
    }
  }

  public static final CompiledNodeEvaluator compileNode( String name, byte[] classdata )
  {
    /* NOTE: BCEL is not thread safe, so synchronize use of the library on
     *       the ClassGen class... we do the same thing in ClassWrapGen
     */
    synchronized(ClassGen.class)
    {
      return (new CompilerContext(name)).compileNodeImpl(classdata);
    }
  }

  /*=======================================================================*/
  /**
   * Class Constructor.
   * 
   * @param name         the name of the class to generate
   */
  private CompilerContext( String name )
  {
    this.name = name;
    this.className = name;
    
    cg = new ClassGen( 
      className,
      "oscript.compiler.CompiledNodeEvaluator",
      name,
      Const.ACC_PUBLIC | Const.ACC_SUPER,
      new String[] {}
    );
    
    cp = cg.getConstantPool();
  }
  
  /*=======================================================================*/
  /**
   * Get the index used for the name of the next <tt>evalNode<i>X</i>()</tt>
   * method.  This is tracked here so an <tt>evalInnerNode</tt> switch method
   * can be constructed with the appropriate number of cases
   * <p>
   * The generated method corresponding to this index, should have the name
   * <tt>"_" + idx + "_" + name</tt>
   */
  int getNextEvalNodeIdx( String name )
  {
    evalNodeNameList.add(name);
    return ++lastEvalNodeIdx;
  }
  
  /*=======================================================================*/
  /**
   * For each function, in addition to having an specific eval method,
   * has a SMIT.  The CompilerContext needs to know it's index, so it
   * can generate an accessor method.
   */
  void addSMITs( int evalNodeIdx, int[] smitIdxs )
  {
    smitIdxTable.put( Integer.valueOf(evalNodeIdx), smitIdxs );
  }
  
  /*=======================================================================*/
  /**
   * The entry point to compile a node.
   * 
   * @param node         the node in syntaxtree to compile
   */
  private byte[] compileNodeImpl( Node node )
  {
    try
    {
      // invoke the compiler to generate the top-most node-evaluator,
      // with inner node-evaluators are generated by recursively constructing
      // CompilerVisitor-s
      cv = new CompilerVisitor( this, "file", node );
      
      // build the constructor that takes an array of objects:
      //    these have to be done after we done with mg
      dumpConstructor();
      dumpEvalInnerNode();
      dumpGetInnerSharedMemberIndexTable();
      dumpInit();
      JavaClass j = cg.getJavaClass();
      //----------------------------------
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      j.dump(bos);      
      byte[] bytes = bos.toByteArray();
      succeeded++;
      return bytes;
    }
    catch(LinkageError e)
    {
      /* this means we hit a bug or limitation of the compiler:
       *
       * note: we can hit this for method bodies that are too big,
       *       so don't treat as fatal error
       */
      compileNodeException(e);
    }
    catch(ClassGenException e)
    {
      /* this means we hit a bug or limitation of the compiler:
       */
      compileNodeException(e);
    }
    catch(Throwable e)
    {
      // treat this as a more fatal sort of error than LinkageError
      compileNodeException(e);
      throw new ProgrammingErrorException(e);
    }
    return null;
  }

  private void compileNodeException( Throwable e )
  {
    String name = this.name;
    if( cv != null )
      name = cv.getDebugName();
    OscriptHost.me.error(failed + ":" + succeeded + "\tError compiling " + name + ": " + e.getMessage());
    failed++;
  }
  
  /**
   * Dump no-arg constructor.  Modifies <code>il</code> and <code>mg</code>
   */
  private final void dumpConstructor()
  {
    InstructionList il = new CompilerInstructionList();
    
    MethodGen mg = new MethodGen(
      Const.ACC_PUBLIC,
      org.apache.bcel.generic.Type.VOID,
      new org.apache.bcel.generic.Type[0],
      new String[0],
      "<init>",
      className,
      il, cp
    );
    
    il.append( InstructionConst.ALOAD_0 );
    il.append( new PUSH( cp, name ) );
    //il.append( new PUSH( cp, cv.getDesc() ) ); ???!??!?!?!?!
    il.append( new INVOKESPECIAL( methodref(
      "oscript.compiler.CompiledNodeEvaluator",
      "<init>",
      "(Ljava/lang/String;)V"					// ;Ljava/lang/String ??
    ) ) );
    
    il.append(initIl);
    il.append( InstructionConst.RETURN );
    
    mg.setMaxStack();
    cg.addMethod( mg.getMethod() );
  }
  
  private final InstructionHandle dumpSwitchDefault( InstructionList il, String msg )
  {
//    il.append( new NEW( cp.addClass("oscript.exceptions.ProgrammingErrorException") ) );
//    il.append( InstructionConst.DUP );
//    InstructionHandle defaultTarget = il.append( new PUSH( cp, msg ) );
//    il.append(
//      new INVOKESPECIAL(
//        methodref(
//          "oscript.exceptions.ProgrammingErrorException",
//          "<init>",
//          "(Ljava/lang/String;)V"
//        )
//      )
//    );
//    il.append( InstructionConst.ATHROW )
InstructionHandle defaultTarget = il.append( InstructionConst.ACONST_NULL );
il.append( InstructionConst.ARETURN );
    return defaultTarget;
  }
  
  private final void dumpEvalInnerNode()
  {
    InstructionList il = new CompilerInstructionList();
    
    MethodGen mg = new MethodGen( 
    		Const.ACC_PUBLIC | Const.ACC_FINAL,
      OBJECT_TYPE,
      new org.apache.bcel.generic.Type[] {
        org.apache.bcel.generic.Type.INT, 
        new ObjectType("oscript.util.StackFrame"), 
        SCOPE_TYPE 
      },
      new String[] { "idx", "sf", "scope" },
      "evalInnerNode",
      className,
      il, cp 
    );
    
    int[] idxs = new int[lastEvalNodeIdx+1];
    InstructionHandle[] targets = new InstructionHandle[lastEvalNodeIdx+1];
    for( int i=0; i<=lastEvalNodeIdx; i++ )
    {
      idxs[i] = i;
      
      targets[i] = il.append( InstructionConst.ALOAD_0 );
      il.append( InstructionConst.ALOAD_2 );
      il.append( new ALOAD(3) );
      il.append( 
        new INVOKEVIRTUAL(
          methodref(
            className,
            "_" + i + "_" + evalNodeNameList.get(i),
            "(Loscript/util/StackFrame;Loscript/data/Scope;)Ljava/lang/Object;"
          )
        )
      );
      il.append( InstructionConst.ARETURN );
    }
    
    // since we need to know the branch targets first, the switch is inserted
    // at the head of the method (in reverse order):
    il.insert(
      new TABLESWITCH(
        idxs,
        targets,
        dumpSwitchDefault(il,"invalid idx")
      )
    );
    il.insert( InstructionConst.ILOAD_1 );
    
    mg.setMaxStack();
    cg.addMethod( mg.getMethod() );
  }
  
  private final void dumpGetInnerSharedMemberIndexTable()
  {
    InstructionList il = new CompilerInstructionList();
    
    MethodGen mg = new MethodGen( 
      Const.ACC_PUBLIC | Const.ACC_FINAL,
      SYMBOL_TABLE_TYPE, new org.apache.bcel.generic.Type[] {
        org.apache.bcel.generic.Type.INT, org.apache.bcel.generic.Type.INT
      },
      new String[] { "idx", "perm" },
      "getInnerSharedMemberIndexTable",
      className, il, cp
    );
    
    int[] idxs = new int[lastEvalNodeIdx + 1];
    InstructionHandle[] idxTargets = new InstructionHandle[lastEvalNodeIdx + 1];
    for( int i=0; i<=lastEvalNodeIdx; i++ )
    {
      InstructionList iil = new CompilerInstructionList();
      idxs[i] = i;
      
      InstructionHandle[] permTargets = new InstructionHandle[oscript.NodeEvaluator.SMIT_PERMS.length];
      for( int j=0; j<oscript.NodeEvaluator.SMIT_PERMS.length; j++ )
      {
        permTargets[j] =
        iil.append( new GETSTATIC( 
          ((int[])(smitIdxTable.get( Integer.valueOf(i) )))[oscript.NodeEvaluator.SMIT_PERMS[j]]
        ) );
        iil.append( InstructionConst.ARETURN );
      }
      
      // since we need to know the branch targets first, the switch is inserted
      // at the head of the method (in reverse order):
      iil.insert( new TABLESWITCH( oscript.NodeEvaluator.SMIT_PERMS,
        permTargets, dumpSwitchDefault( iil, "invalid perm" ) ) 
      );
      idxTargets[i] = iil.insert( InstructionConst.ILOAD_2 );
      il.append( iil );
    }
    
    // since we need to know the branch targets first, the switch is inserted
    // at the head of the method (in reverse order):
    il.insert( new TABLESWITCH( idxs, idxTargets, dumpSwitchDefault( il, "invalid idx" ) ) );
    il.insert( InstructionConst.ILOAD_1 );
    
    mg.setMaxStack();
    cg.addMethod( mg.getMethod() );
  }
  
  private final void dumpInit()
  {
    InstructionList il = clinitIl;
    
    MethodGen mg = new MethodGen(
      Const.ACC_PUBLIC,
      org.apache.bcel.generic.Type.VOID,
      new org.apache.bcel.generic.Type[0],
      new String[0],
      "<clinit>",
      className,
      il, cp
    );
    
    il.append( InstructionConst.RETURN );
    
    mg.setMaxStack();
    cg.addMethod( mg.getMethod() );
  }
  
  /*=======================================================================*/
  static Class makeAccessible( Class c )
  {
    // deal with classes that we don't have access to by using the
    // classes parent type:
    while( ! java.lang.reflect.Modifier.isPublic(c.getModifiers()) )
      c = c.getSuperclass();
    
    return c;
  }
  
  /*=======================================================================*/
  int methodref( String className, String methodName, String sig )
  {
    return cp.addMethodref( className, methodName, sig );
  }
  
  int ifmethodref( String className, String methodName, String sig )
  {
    return cp.addInterfaceMethodref( className, methodName, sig );
  }
  
  int fieldref( String className, String methodName, String sig )
  {
    return cp.addFieldref( className, methodName, sig );
  }
  
  /*=======================================================================*/
  private static int identifierCnt = 0;
  static String makeUniqueIdentifierName( String name )
  {
    return name + Math.abs(++identifierCnt);
  }
  
  /*=======================================================================*/
  void pushInt( InstructionList il, int i )
  {
    if( (i >= -1) && (i <= 5) )
      il.append( new ICONST(i) );
    else
      il.append( new LDC( cp.addInteger(i) ) );
  }
  
  /*=======================================================================*/
  /**
   */
  int makeField( String name, Class cls )
  {
    String fieldName = makeUniqueIdentifierName("c");
    
    org.apache.bcel.generic.Type type = getType(cls);
    
    FieldGen fg = new FieldGen(
      Const.ACC_PRIVATE | Const.ACC_STATIC, 
      type,
      fieldName,
      cp
    );
    
    cg.addField( fg.getField() );
    
    return fieldref( className, fieldName, type.getSignature() );
  }
  
  /*=======================================================================*/
  /**
   * Get the instance variable index of the specified object.  The first
   * time this is called with a given object, this creates an member
   * variable within the constructed class.
   */
  int getInstanceConstantIdx( Object obj )
  {
    Integer iidx = (Integer)(constFieldRefTable.get(obj));
    
    // special case to handle the int[] argIds parameter for functions,
    // since two identical arrays won't appear as equals() to the hashtable
    if( (iidx == null) && (obj.getClass() == int[].class) )
    {
      int[] arr = (int[])obj;
      for( Iterator itr=constFieldRefTable.keySet().iterator(); itr.hasNext(); )
      {
        Object key = itr.next();
        if( (key.getClass() == int[].class) && java.util.Arrays.equals( arr, (int[])key ) )
        {
          iidx = (Integer)(constFieldRefTable.get(key));
          break;
        }
      }
    }
    
    if( iidx == null )
    {
      Class c = obj.getClass();
      
      // hack to deal with RegExp:
      if( obj instanceof RegExp )
        c = RegExp.class;
      
      int idx = makeField( "c", c );
      
      if( c == OString.class )
      {
        clinitIl.append( new PUSH( cp, ((OString)obj).castToString() ) );
        clinitIl.append( new INVOKESTATIC( methodref(
          "oscript.data.OString",
          "makeString",
          "(Ljava/lang/String;)Loscript/data/OString;"
        ) ) );
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else if( c == OExactNumber.class )
      {
        clinitIl.append( new PUSH( cp, ((OExactNumber)obj).castToExactNumber() ) );
        clinitIl.append( new INVOKESTATIC( methodref(
          "oscript.data.OExactNumber",
          "makeExactNumber",
          "(J)Loscript/data/OExactNumber;"
        ) ) );
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else if( c == OInexactNumber.class )
      {
        clinitIl.append( new PUSH( cp, ((OInexactNumber)obj).castToInexactNumber() ) );
        clinitIl.append( new INVOKESTATIC( methodref(
          "oscript.data.OInexactNumber",
          "makeInexactNumber",
          "(D)Loscript/data/OInexactNumber;"
        ) ) );
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else if( c == RegExp.class )
      {
        clinitIl.append( new PUSH( cp, ((RegExp)obj).castToString() ) );
        clinitIl.append( new INVOKESTATIC( methodref(
          "oscript.data.OString",
          "makeString",
          "(Ljava/lang/String;)Loscript/data/OString;"
        ) ) );
        clinitIl.append( new INVOKESTATIC( methodref(
          "oscript.data.RegExp",
          "createRegExp",
          "(Loscript/data/Value;)Loscript/data/RegExp;"
        ) ) );
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else if( c == OpenHashSymbolTable.class )
      {
        OpenHashSymbolTable st = (OpenHashSymbolTable)obj;
        clinitIl.append( new NEW( cp.addClass("oscript.util.OpenHashSymbolTable") ) );
        clinitIl.append( InstructionConst.DUP );
        clinitIl.append( new PUSH( cp, st.size() ) );
        clinitIl.append( new PUSH( cp, 0.75f ) );
        clinitIl.append( new INVOKESPECIAL( methodref(
          "oscript.util.OpenHashSymbolTable",
          "<init>",
          "(IF)V"
        ) ) );
        int[] idxs = new int[st.size()];
        for( Iterator itr=st.symbols(); itr.hasNext(); )
        {
          int id = ((Integer)(itr.next())).intValue();
          idxs[ st.get(id) ] = id;
        }
        for( int i=0; i<idxs.length; i++ )
        {
          clinitIl.append( InstructionConst.DUP );
          pushSymbol( clinitIl, Symbol.getSymbol( idxs[i] ).castToString() );
          clinitIl.append( new INVOKEVIRTUAL( methodref(
            "oscript.util.OpenHashSymbolTable",
            "create",
            "(I)I"
          ) ) );
          clinitIl.append( InstructionConst.POP );
        }
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else if( c == int[].class )
      {
        int[] val = (int[])obj;
        
        pushInt( clinitIl, val.length );
        clinitIl.append( new NEWARRAY( org.apache.bcel.generic.Type.INT ) );
        
        // array consists of [symbol,attr]*
        for( int i=0; i<val.length; )
        {
          clinitIl.append( InstructionConst.DUP );
          pushInt( clinitIl, i );
          pushSymbol( clinitIl, Symbol.getSymbol(val[i]).castToString() );
          clinitIl.append( InstructionConst.IASTORE );
          i++;
          
          clinitIl.append( InstructionConst.DUP );
          pushInt( clinitIl, i );
          pushInt( clinitIl, val[i] );
          clinitIl.append( InstructionConst.IASTORE );
          i++;
        }
        
        clinitIl.append( new PUTSTATIC(idx) );
      }
      else
      {
        throw new ProgrammingErrorException("instance-constant: " + obj + "(" + obj.getClass().getName() + ")");
      }
      
      constFieldRefTable.put( obj, iidx=Integer.valueOf(idx) );
    }
    
    return iidx.intValue();
  }
  
  /*=======================================================================*/
  /**
   * push the instance constant onto the stack, setting ret-val to true.
   */
  void pushInstanceConstant( InstructionList il, Object obj )
  {
    // first check for null, no need to store that as an instance const:
    if( obj == null )
    {
      il.append( InstructionConst.ACONST_NULL );
      return;
    }
    
    if( obj == Value.UNDEFINED )
      il.append( new GETSTATIC( fieldref( "oscript.data.Value", "UNDEFINED", "Loscript/data/Value;" ) ) );
    else if( obj == Value.NULL )
      il.append( new GETSTATIC( fieldref( "oscript.data.Value", "NULL", "Loscript/data/Value;" ) ) );
    else if( obj == OBoolean.TRUE )
      il.append( new GETSTATIC( fieldref( "oscript.data.OBoolean", "TRUE", "Loscript/data/OBoolean;" ) ) );
    else if( obj == OBoolean.FALSE )
      il.append( new GETSTATIC( fieldref( "oscript.data.OBoolean", "FALSE", "Loscript/data/OBoolean;" ) ) );
    else
      il.append( new GETSTATIC( getInstanceConstantIdx(obj) ) );
  }
  
  /*=======================================================================*/
  /**
   * dump a System.err.println() call, a debugging tool for compiled code
   */
//  private void dumpLog( InstructionList il, String str )
//  {
//    il.append( new GETSTATIC( fieldref( "java.lang.System", "err", "Ljava/io/PrintStream;" ) ) );
//    il.append( new PUSH( cp, str ) );
//    il.append( new INVOKEVIRTUAL( methodref( "java.io.PrintStream", "println", "(Ljava/lang/String;)V" ) ) );
//  }
  
  /*=======================================================================*/
  /**
   * as long as this is just a helper to pushFunctionData, no need to
   * cache...
   */
  private void pushNodeEvaluator( InstructionList il, int id, int idx )
  {
    if( idx == -1 )
    {
      il.append( InstructionConst.ACONST_NULL );
      return;
    }
    
    il.append( new NEW( cp.addClass("oscript.compiler.CompiledInnerNodeEvaluator") ) );
    il.append( InstructionConst.DUP );
    pushSymbol( il, Symbol.getSymbol(id).castToString() );
    il.append( new PUSH( cp, idx ) );
    il.append( InstructionConst.ALOAD_0 );
    il.append( new INVOKESPECIAL( methodref(
      "oscript.compiler.CompiledInnerNodeEvaluator",
      "<init>",
      "(IILoscript/compiler/CompiledNodeEvaluator;)V"
    ) ) );
    
  }
  
  /*=======================================================================*/
  /**
   * push FunctionData on the stack, but cached
   */
  void pushFunctionData( InstructionList il,
                         int id, int[] argIds, boolean varargs, 
                         int extendsIdx, int fxnIdx, int staticIdx,
                         boolean hasVarInScope, boolean hasFxnInScope,
                         Value comment )
  {
    int idx = makeField( "fd", FunctionData.class );
    
    initIl.append( new NEW( cp.addClass("oscript.data.FunctionData") ) );
    initIl.append( InstructionConst.DUP );
    pushSymbol( initIl, Symbol.getSymbol(id).castToString() );
    pushInstanceConstant( initIl, argIds );
    initIl.append( new PUSH( cp, varargs ) );
    pushNodeEvaluator( initIl, id, extendsIdx );
    pushNodeEvaluator( initIl, id, fxnIdx );
    pushNodeEvaluator( initIl, id, staticIdx );
    initIl.append( new PUSH( cp, hasVarInScope ) );
    initIl.append( new PUSH( cp, hasFxnInScope ) );
    pushInstanceConstant( initIl, comment );
    initIl.append( new INVOKESPECIAL( methodref(
      "oscript.data.FunctionData",
      "<init>",
      "(I[IZLoscript/NodeEvaluator;Loscript/NodeEvaluator;Loscript/NodeEvaluator;ZZLoscript/data/Value;)V"
    ) ) );
    initIl.append( new PUTSTATIC(idx) );
    
    il.append( new GETSTATIC(idx) );
  }
  
  /*=======================================================================*/
  /**
   * Push symbol id (int) on stack.  Note that the numeric value of the
   * symbol may be different on differerent invocations of the script
   * engine.  In the case of <code>SymbolTable</code>s, this is taken
   * into account when serializing, so when the table is re-created it
   * is using the current numeric values of the symbol.
   * 
   * @return the current symbol value (ie. with the current symbol table,
   *    but could be different when code is reloaded into a different
   *    invokation of the script env)
   */
  void pushSymbol( InstructionList il, String name )
  {
    Integer iidx = (Integer)(symbolFieldRefTable.get(name));
    if( iidx == null )
    {
      int idx = makeField( name, int.class );
      iidx = Integer.valueOf(idx);
      clinitIl.append( new PUSH( cp, name ) );
      clinitIl.append( new INVOKESTATIC( methodref(
        "oscript.data.Symbol",
        "getSymbol",
        "(Ljava/lang/String;)Loscript/data/Symbol;"
      ) ) );
      clinitIl.append( new INVOKEVIRTUAL( methodref(
        "oscript.data.Symbol",
        "getId",
        "()I"
      ) ) );
      clinitIl.append( new PUTSTATIC(idx) );
      symbolFieldRefTable.put( name, iidx );
    }
    il.append( new GETSTATIC( iidx.intValue() ) );
  }
  
  /*=======================================================================*/
  static org.apache.bcel.generic.Type getType( Class c )
  {
    if( c.isArray() )
      return new ArrayType( getType( c.getComponentType() ), 1 );
    else if( c.isPrimitive() )
      return getPrimitiveType(c);
    else
      return new ObjectType( CompilerContext.makeAccessible(c).getName() );
  }
  
  /*=======================================================================*/
  static org.apache.bcel.generic.Type getPrimitiveType( Class c )
  {
    if( c == Boolean.TYPE )
      return org.apache.bcel.generic.Type.BOOLEAN;
    else if( c == Integer.TYPE )
      return org.apache.bcel.generic.Type.INT;
    else if( c == Short.TYPE )
      return org.apache.bcel.generic.Type.SHORT;
    else if( c == Byte.TYPE )
      return org.apache.bcel.generic.Type.BYTE;
    else if( c == Long.TYPE )
      return org.apache.bcel.generic.Type.LONG;
    else if( c == Double.TYPE )
      return org.apache.bcel.generic.Type.DOUBLE;
    else if( c == Float.TYPE )
      return org.apache.bcel.generic.Type.FLOAT;
    else if( c == Character.TYPE )
      return org.apache.bcel.generic.Type.CHAR;
    else
      throw new ProgrammingErrorException("bad primitive type: " + c);
  }

  //---------------------------------------------------------------------
  public static interface HiddenClassByteLoaderApi {
	  public Class load(byte[] classdata) throws IllegalAccessException; 
  }
  private final static HiddenClassByteLoaderApi classDataLoader;
  static {
	  HiddenClassByteLoaderApi api = null;
	  try 
	  {
		  api=(HiddenClassByteLoaderApi) Class.forName("OscriptHiddenClassBytesLoader").
				  getMethod("getImpl").
				  invoke(null);
	  } catch (Throwable e) {
		  OscriptHost.me.error(e+"");
	  }
	  classDataLoader = api;
  }
  //---------------------------------------------------------------------
  private CompiledNodeEvaluator compileNodeImpl( byte[] classdata)
  {
    try
    {
      Class c = classDataLoader.load(classdata);
      CompiledNodeEvaluator result = (CompiledNodeEvaluator)(c.getConstructor().newInstance());
      succeeded++;
      return result;
    } catch(Throwable e)
    {
      // treat this as a more fatal sort of error than LinkageError
      compileNodeException(e);
      throw new ProgrammingErrorException(e);
    }
  }
}


