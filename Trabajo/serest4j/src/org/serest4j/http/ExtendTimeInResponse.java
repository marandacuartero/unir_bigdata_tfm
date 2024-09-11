package org.serest4j.http;

import java.io.IOException;

import org.serest4j.cripto.KeyFactory;

import jakarta.servlet.ServletResponse;


public class ExtendTimeInResponse {

	public static void procesar(int segundosDeEspera) {
		try {
			procesar(segundosDeEspera, null, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void procesar(int segundosDeEspera, String str, ServletResponse servletResponse) throws IOException {
		if( str == null ) {
			long lespera = segundosDeEspera * 1000l;
			lespera = Math.max(100l,  lespera);
			long lfinal = System.currentTimeMillis() + lespera;
			while( lespera > 0 ) {
				try { Thread.sleep(lespera); }catch (InterruptedException ie) {}
				lespera = lfinal - System.currentTimeMillis();
			}
		}
		else {
			long lespera = segundosDeEspera * 1000l;
			lespera = Math.max(1000l,  lespera);
			long lfinal = System.currentTimeMillis() + lespera;
			while( lespera > 0 ) {
				servletResponse.getOutputStream().write(KeyFactory.make());
				servletResponse.getOutputStream().write(str.getBytes("UTF-8"));
				try { Thread.sleep(10l); }catch (InterruptedException ie) {}
				lespera = lfinal - System.currentTimeMillis();
			}
		}
	}
}
