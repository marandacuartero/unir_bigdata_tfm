package org.serest4j.cripto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Clase de uso interno para envio y recepcion de datos encriptados
 * 
 * @author Maranda
 *
 */

public class Clarifier {

	public static byte[][] desencripta1(ObjectInputStream ois) throws IOException {
		String str = ois.readUTF();
		if( str.charAt(0) == '#'  &&  str.charAt(str.length() - 1) == '#' ) {
			int nchunks = Integer.parseInt(str.substring(1, str.length() - 1), 23);
			StringBuilder sb = new StringBuilder(nchunks * 65000);
			for( int i = 0; i<nchunks; i++ ) {
				sb.append(ois.readUTF());
			}
			return desencripta1(sb.toString());
		}
		else {
			return desencripta1(str);
		}
	}

	public static byte[][] desencripta1(ByteArrayOutputStream datosbase64) throws IOException {
		return desencripta1(datosbase64.toString("UTF-8"));
	}

	public static byte[][] desencripta1(String datosbase64) throws IOException {
		byte[] b = Base64.getDecoder().decode(datosbase64);
		byte[][] resultado = desincrusta(b);
		b = resultado[1];
		GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(b));
		ByteArrayOutputStream bout = new ByteArrayOutputStream(100 * 1024);
		bout.reset();
		b = new byte[512];
		int n = gin.read(b);
		while(n >= 0) {
			bout.write(b, 0, n);
			n = gin.read(b);
		}
		b = bout.toByteArray();
		resultado = desincrusta(b);
		return resultado;
	}

	public static Object[] desencripta2(byte[] clave, byte[] datos) throws IOException, ClassNotFoundException {
		NoiseFactory.cambiame(clave, datos);
		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(datos));
		int n = in.readInt();
		Object[] obj = new Object[n];
		for( int i=0; i<n; i++ ) {
			obj[i] = Serializator.deserializa(in);
		}
		in.close();
		return obj;
	}

	private static byte[][] desincrusta(byte[] b) {
		int blm = b.length / 2;
		byte[] bstrndatos = new byte[12];
		for( int k=0; k<bstrndatos.length; k++ ) {
			bstrndatos[bstrndatos.length - k - 1] = b[blm + k];	
		}
		int nclave = Integer.parseInt(new String(bstrndatos, 2, 3), 16);
		int ndatos = Integer.parseInt(new String(bstrndatos, 5, 6), 16);
		System.arraycopy(b, blm + 12, b, blm, b.length - blm - 12);
		byte[] idClave = new byte[nclave];
		byte[] datos = new byte[ndatos];
		int i=0;
		for( ; i<nclave; i++ ) {
			idClave[i] = b[2*i];
			if( i < datos.length )
				datos[i] = b[2*i + 1];
		}
		if( i < datos.length ) {
			System.arraycopy(b, 2*i, datos, i, datos.length - i);
		}
		return new byte[][]{idClave, datos};
	}
}
