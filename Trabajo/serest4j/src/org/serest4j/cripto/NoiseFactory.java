package org.serest4j.cripto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

/**
 * Clase de uso interno para envio y recepcion de datos encriptados
 * 
 * @author Maranda
 *
 */
public class NoiseFactory {

	/**
	 * Encripta para el protocolo de intercambio de datos
	 * y devuelve una cadena en base 64
	 * 
	 * @return
	 * @throws IOException 
	 */
	public static String[] encriptaChunked(String idClave, byte[] clave, Object[] datos, Logger logger) throws IOException {
		String strRetorno = encripta(idClave, clave, datos, logger);
		int nmax = 65000;
		int imax = strRetorno.length() / nmax;
		if( imax <= 0 ) {
			return new String[]{strRetorno};
		}
		else {
			int resto = strRetorno.length() % nmax;
			int nchunks = imax + ( resto > 0 ? 1 : 0);
			String[] strResp = new String[nchunks + 1];
			strResp[0] = "#" + Integer.toString(nchunks, 23) + "#";
			for( int i = 0; i < imax; i++ ) {
				strResp[i + 1] = strRetorno.substring(i * nmax, (i + 1) * nmax);
			}
			if( resto > 0 ) {
				strResp[strResp.length - 1] = strRetorno.substring(imax * nmax, imax * nmax + resto);
			}
			return strResp;
		}
	}

	public static String encripta(String idClave, byte[] clave, Object[] datos, Logger logger) throws IOException {
		byte[] b = Serializator.serializa(datos, logger);
		cambiame(clave, b);
		b = incrusta(idClave == null ? new byte[0] : idClave.getBytes("UTF-8"), b);
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		GZIPOutputStream gout = new GZIPOutputStream(bout);
		gout.write(b);
		gout.flush();
		gout.close();
		b = bout.toByteArray();
		Random r = new Random();
		byte[] b2 = new byte[20];
		r.nextBytes(b2);
		b = incrusta(b2, b);
		return Base64.getEncoder().encodeToString(b);
	}

	private static byte[] incrusta(byte[] idClave, byte[] datos) throws UnsupportedEncodingException {
		int ndatos = datos.length;
		int nclave = idClave.length;
		int _nbsize = ndatos > nclave ? (ndatos + nclave) : 2 * nclave;
		_nbsize += 12;
		byte[] b = new byte[Math.max(_nbsize, 256)];
		Random random = new Random();
		random.nextBytes(b);
		int i=0;
		for( ; i<nclave; i++ ) {
			b[2*i] = idClave[i];
			if( i < datos.length )
				b[2*i + 1] = datos[i];
		}
		if( i < datos.length ) {
			System.arraycopy(datos, i, b, 2*i, datos.length - i);
		}
		byte[] bstrdatos = null;
		StringBuffer buffer = new StringBuffer(100 * 1024);
		buffer.setLength(0);
		buffer.append("00");
		int l1 = buffer.length();
		buffer.append("000").append(Integer.toHexString(nclave));
		int l2 = buffer.length() - 3;
		buffer.delete(l1, l2);
		l1 = buffer.length();
		buffer.append("000000").append(Integer.toHexString(ndatos));
		l2 = buffer.length() - 6;
		buffer.delete(l1, l2);
		buffer.append("0");
		bstrdatos = buffer.toString().getBytes("UTF-8");
		bstrdatos[0] = (byte)(random.nextInt(256));
		bstrdatos[1] = (byte)(random.nextInt(256));
		bstrdatos[bstrdatos.length - 1] = (byte)(random.nextInt(256));
		int blm = b.length / 2;
		System.arraycopy(b, blm, b, blm + 12, b.length - blm - 12);
		for( int k=0; k<bstrdatos.length; k++ ) {
			b[blm + k] = bstrdatos[bstrdatos.length - k - 1];
		}
		return b;
	}

	static void cambiame(byte[] clave, byte[] datos) {
		if( clave != null  &&  clave.length > 0 ) {
			int j = 0;
			int n = clave.length;
			for( int i=0; i<datos.length; i++ ) {
				if( j >= n )
					j = 0;
				datos[i] = (byte)(datos[i] ^ clave[j]);
				j++;
			}
		}
	}

	public static String md5(String s) {
		String r = null;
		try {
			if (s != null) {
				MessageDigest algorithm = MessageDigest.getInstance("MD5");
				algorithm.reset();
				algorithm.update(s.getBytes("UTF-8"));
				byte bytes[] = algorithm.digest();
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < bytes.length; i++) {
					String hex = Integer.toHexString(0xff & bytes[i]);
					if (hex.length() == 1)
						sb.append('0');
					sb.append(hex);
				}
				r = sb.toString();
			}
		} catch (Exception e) { e.printStackTrace(); }
		return r;
	}
}
