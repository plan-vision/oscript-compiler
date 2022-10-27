import java.lang.invoke.MethodHandles;

import oscript.compiler.CompilerContext.HiddenClassByteLoaderApi;

/* important to be in the default package */
public class OscriptHiddenClassBytesLoader 
{
	public static HiddenClassByteLoaderApi getImpl() 
	{
		return new HiddenClassByteLoaderApi() 
		{
			@Override
			public Class load(byte[] classdata) throws IllegalAccessException 
			{
				MethodHandles.Lookup lookup = MethodHandles.lookup();
				return lookup.defineHiddenClass(classdata, true/*,java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE*/).lookupClass();
			}
			
		};
	}
}
