package org.serest4j.db;

import java.sql.Connection;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * Encapsulador de transacciones que permite al sistema gestionar los accesos a base de datos y realizar una trazabilidad
 * de las consultas realizadas.
 * 
 * @author Maranda
 *
 */
public class DefaultTransactionalLogger implements TMTransactionalLogger
{
	private static final char[] CADENA_INICIO = "\\\\".toCharArray();

	private final String name;
	private final Connection conexion;
	private final long inicio; 

	private StringBuilder log;
	private Logger debug;

	private int i, puntoDeInsercion;
	private long m;
	private boolean closed = false;

	public DefaultTransactionalLogger(Connection c, StringBuilder sb, Logger debug) {
		this(null, null, null, c, sb, debug);
	}

	/**
	 * 
	 * @param n El nombre de la transaccion
	 * @param c La conexion a base de datos asociada
	 */
	public DefaultTransactionalLogger(String userName, String sourceName, String serviceName, Connection c, StringBuilder sb, Logger debug) {
		this.conexion = c;
		this.debug = debug;
		this.inicio = System.currentTimeMillis();
		this.m = inicio;
		i = 0;
		name = serviceName == null ? "" : serviceName;
		log = null;
		if( sb != null ) {
			log = sb;
			log.append('\n').append(new Date(inicio));
			log.append('\n').append(CADENA_INICIO).append(userName == null ? "" : "[" + userName.trim() + "]");
			log.append(CADENA_INICIO).append(sourceName == null ? "" : sourceName.trim()).append(CADENA_INICIO);
			log.append("Inicio de ").append(name).append(CADENA_INICIO);
			puntoDeInsercion = log.length();
			log.append('\n');
		}
	}

	public String getName() {
		return name;
	}

	public void flush() {
		if( hashLogger() ) {
			mark();
			if( debug != null ) {
				String str = log.substring(puntoDeInsercion + 1, log.length());
				debug.debug(str);
			}
			log.setLength(puntoDeInsercion);
			log.append('\n');
		}
	}

	/**
	 * 
	 * @return El log de escritura de la transaccion
	 */
	public StringBuilder getLogger() { return log; }

	public boolean hashLogger() { return !closed  &&  log != null; }

	@Override
	public String toString() {
		if( isClosed() ) {
			if( log == null )
				return "";
			else
				return String.valueOf(log);
		}
		else {
			return "NOT CLOSED!! " + log;
		}
	}

	/**
	 * Imprime una linea independiente en el log de la transaccion
	 * 
	 * @param str
	 */
	public void println(String str) {
		if( hashLogger() ) {
			log.append('\n').append(str).append('\n');
		}
	}

	public void printThrowable(Throwable th) {
		if( hashLogger() ) {
			log.append('\n').append('\n');
			log.append("-- ").append(th).append('\n');
			String nmqb = getClass().getName();
			for( StackTraceElement ste : th.getStackTrace() ) {
				if( ste.getClassName().equals(nmqb) ) {
					log.append("-- \t").append(ste.toString()).append('\n');
				}
			}
		}
	}

	/**
	 * @return La conexion asociada a la transaccion
	 */
	public Connection getConexion() { return conexion; }

	/**
	 * Establece un punto intermedio de referencia dentro de la transaccion, donde mide el tiempo de ejecucion desde el punto anterior de referencia
	 * 
	 */
	public void mark() {
		if( log != null ) {  
			long l = System.currentTimeMillis() - m;
			log.append("\n--   [[  Marca[").append(i).append(']').append(':').append(l).append(" msegs  ]\n");
			m = System.currentTimeMillis();
			i++;
		}
	}

	public long duracion() {
		return System.currentTimeMillis() - inicio;
	}

	public void close() {
		if( log != null  &&  !closed ) {  
			long l = System.currentTimeMillis() - inicio;
			int i1 = log.length();
			log.append("\n--   [   Total :").append(l).append(" msegs  ]");
			int i2 = log.length();
			char[] c = new char[i2 - i1];
			log.getChars(i1, i2, c, 0);
			log.deleteCharAt(puntoDeInsercion);
			log.insert(puntoDeInsercion, c);
			log.append('\n');
			log.append(CADENA_INICIO).append(CADENA_INICIO).append(" Fin de ").append(name).append(CADENA_INICIO).append('\n');
		}
		closed = true;
	}

	public boolean isClosed() {
		return closed;
	}
}
