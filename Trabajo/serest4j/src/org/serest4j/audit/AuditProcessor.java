package org.serest4j.audit;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

public class AuditProcessor {

	private static AtomicReference<Object> auditoriaGenericaRef = new AtomicReference<Object>(null);
	private static ThreadPoolExecutor asynchronousProcessor;

	public synchronized static void init(String className, Logger debug) {
		if( auditoriaGenericaRef.get() == null ) {
			LinkedBlockingQueue<Runnable> _colaProceso = new LinkedBlockingQueue<Runnable>();
			asynchronousProcessor = new ThreadPoolExecutor(10, 30, 30, TimeUnit.SECONDS, _colaProceso);
			if( className != null  &&  className.trim().length() > 0 ) {
				try {
					Class<?> clase = AuditProcessor.class.getClassLoader().loadClass(className.trim());
					if( clase != null  &&  !clase.isInterface()  &&  AuditInterface.class.isAssignableFrom(clase) ) {
						auditoriaGenericaRef.compareAndSet(null, clase.getDeclaredConstructor().newInstance());
						if( debug != null ) {
							debug.debug("AuditoriaGenerica.init(" + className + ") genera " + auditoriaGenericaRef.get());
						}
					}
				} catch (Exception e) {
					if( debug != null ) {
						debug.error("AuditoriaGenerica.init(" + className + ")", e);
					}
					else {
						e.printStackTrace();
					}
				}
			}
			else {
				if( debug != null ) {
					debug.error("No se han definido librerias para gestion de auditorias");
				}
			}
		}
	}

	public static void auditarAuditoriaGenerica(Object usuario, Object retorno, String controlador, String servicio, Object... argumentos) {
		Object auditoriaGenerica = auditoriaGenericaRef.get();
		if( auditoriaGenerica != null  &&  auditoriaGenerica instanceof AuditInterface ) {
			AuditInterface ai = (AuditInterface)auditoriaGenerica;
			put(new AuditAsynchronousAdapter(ai, usuario, controlador, servicio, retorno, argumentos));
		}
	}

	public static void put(Runnable ai) {
		if( ai != null  &&  asynchronousProcessor != null ) {
			asynchronousProcessor.submit(ai);
		}
	}
}
