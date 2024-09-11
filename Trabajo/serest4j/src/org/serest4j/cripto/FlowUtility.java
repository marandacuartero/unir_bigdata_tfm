package org.serest4j.cripto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FlowUtility {

	public static void flushStream(InputStream is, OutputStream out) throws IOException
	{
		if( is != null ) {
			byte[] b = new byte[512];
			int n = is.read(b);
			while(n >= 0) {
				if( out != null ) {
					out.write(b, 0, n);	
				}
				n = is.read(b);
			}
		}
	}

	public static void changeStream(byte[] clave, InputStream in, OutputStream out) throws IOException {
		if( clave == null ) {
			throw new IOException("No existe clave asociada");
		}
		else if( clave.length <= 0 ) {
			flushStream(in, out);
		}
		else {
			int n = clave.length;
			int dato = in.read();
			int j = 0;
			while( dato != -1 ) {
				if( j >= n )
					j = 0;
				dato = (byte)dato ^ clave[j];
				j++;
				out.write(dato);
				dato = in.read();
			}
			out.flush();
		}
	}
}
