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
 */

package oscript.compiler;
import oscript.OscriptHost;
import oscript.syntaxtree.Node;

/**
 * A front end for the compiler, making it match the
 * {@link NodeEvaluatorFactory} interface
 * 
 * @author Rob Clark (rob@ti.com)
 */
public class CompiledNodeEvaluatorFactory implements
		oscript.NodeEvaluatorFactory {
	
	public oscript.NodeEvaluator createNodeEvaluator(String name, Node node) {
		byte[] b = CompilerContext.compileNode(OscriptHost.me.nodeNameToClassName(name), node); 
		return createNodeEvaluator(name,b);
	}

	public oscript.NodeEvaluator createNodeEvaluator(String name, byte[] classdata) {
		return CompilerContext.compileNode(OscriptHost.me.nodeNameToClassName(name), classdata);
	}

}
