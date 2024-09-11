package org.serest4j.cripto;

import java.util.Random;

/**
 * Libreria para generacion de claves binarias para encriptacion de datos
 * 
 * @author Maranda
 *
 */
public class KeyFactory {

	public static final int LONGITUD = 1024;

	public static byte[] make() {
		Random r = new Random();
		int n = LONGITUD + r.nextInt(128) + 1;
		byte[] clave = new byte[n];
		for( int i=0; i<n; i++ ) {
			byte b = (byte)(0x0ff & r.nextInt(LONGITUD));
			clave[i] = b;
		}
		return clave;
	}
}
