package org.serest4j.cripto;

import java.util.Random;
import java.util.UUID;

/**
 * Clase para generacion de identificadores aleatorios basados en secuencias de digitos y letras
 * 
 * @author Maranda
 *
 */
public class TokenFactory {

	private static int MD5SIZE = 32;
	private static final int LONGITUD = 0x6f;

	public static String make() {
		Random r = new Random();
		StringBuilder sb = new StringBuilder(UUID.randomUUID().toString()).append(UUID.randomUUID().toString());
		int n = sb.length();
		for( int i=n-1; i>=0; i-- ) {
			char c = sb.charAt(i);
			if( !Character.isLetterOrDigit(c) ) {
				sb.deleteCharAt(i);
			}
		}
		StringBuilder cadena = new StringBuilder();
		while( sb.length() < LONGITUD ) {
			cadena.setLength(0);
			cadena.append(sb.substring(0, MD5SIZE).toLowerCase()).reverse();
			sb.insert(0, NoiseFactory.md5(cadena.toString()));
		}
		n = sb.length();
		for( int i=n - 1; i>=0; i-- ) {
			char c = sb.charAt(i);
			if( Character.isLetter(c) ) {
				c = r.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c);
				sb.setCharAt(i, c);
			}
		}
		return sb.toString();
	}

	public static boolean verify(String id) {
		if( id == null  ||  id.length() < LONGITUD ) {
			return false;
		}
		while( id.length() >= 3 * MD5SIZE ) {
			String strmd5 = id.substring(0, MD5SIZE).toLowerCase();
			id = id.substring(MD5SIZE);
			String strtoken = new StringBuilder(id.substring(0, MD5SIZE)).reverse().toString().toLowerCase();
			if( !strmd5.equals(NoiseFactory.md5(strtoken)) ) {
				return false;
			}
		}
		return true;
	}
}
