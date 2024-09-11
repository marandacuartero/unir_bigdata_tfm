package org.serest4j.db;

import java.sql.SQLException;
import java.util.Arrays;

import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.serest4j.annotation.db.TMDataSource;
import org.serest4j.annotation.db.TMDriverParameters;
import org.serest4j.annotation.db.TMNoDataSource;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;

/**
 * Es una clase que incorpora las herramientas basicas para la gestion transaccional y accesos a base de datos
 * 
 * @author Maranda
 * 
 * @see TMDataSource
 * @see TMDriverParameters
 */
public class TransactionalBaseContainer {

	private TransactionalPool CONNECTION_POOL;
	private String source;

	
	protected Logger debug;
	protected Logger error;
	protected String loggerName;
	private Logger userLogger;
	private Logger defaultLogger;

	protected TransactionalBaseContainer() {
		this.defaultLogger = Logger.getLogger(TransactionalBaseContainer.class);
	}
	
	/**
	 * @param class4DataSource La clase del controlador sobre el que realizaremos las operaciones transaccionales. Su funcion es proporcionar informacion sobre
	 * las conexiones a base de datos.
	 * @param loggerName Nos sirve para definir el nombre del fichero de Loggers. Si es null toma el nombre de la clase anterior por defecto.
	 * @throws SQLException
	 * @throws NamingException
	 */
	protected void initTransaction(PropertiesLoader gssProperties, Class<?> class4DataSource, Logger userLogger) throws SQLException, NamingException {
		this.userLogger = userLogger;
		this.error = FileLogger.getLogger(gssProperties.getContexto(), class4DataSource);
		this.debug = this.error.isDebugEnabled() ? this.error : null;

		this.error.trace("Creado " + this);
		if( class4DataSource.isAnnotationPresent(TMNoDataSource.class) ) {
			CONNECTION_POOL = null;
			this.source = "NO DATA SOURCE";
		}
		else if( class4DataSource.isAnnotationPresent(TMDataSource.class) ) {
			// Obtengo el data source, si la clase contiene la anotacion DataSource
			TMDataSource ds = class4DataSource.getAnnotation(TMDataSource.class);
			if( ds.value() != null  &&  ds.value().trim().length() > 0 ) {
				this.source = gssProperties.getProperty(ds.value().trim(), ds.value().trim());
				CONNECTION_POOL = new DSTransactionalPool(this.source);
				traceError("DataSource=CONNECTION_POOL (" + this.source + ") " + CONNECTION_POOL);
			}
			else {
				CONNECTION_POOL = null;
				this.source = "NO DATA SOURCE";
			}
		}
		// Obtengo la conexion JDBC, si la clase contiene la anotacion DriverParameters
		else if( class4DataSource.isAnnotationPresent(TMDriverParameters.class) ) {
			TMDriverParameters ds = class4DataSource.getAnnotation(TMDriverParameters.class);
			String str = ds.value();
			String driver = gssProperties.getProperty(str + ".driver");
			String connectString = gssProperties.getProperty(str + ".url");
			String user = gssProperties.getProperty(str + ".user");
			String password = gssProperties.getProperty(str + ".pwd");
			String controlQuery = gssProperties.getProperty(str + ".query");
			int maximo = gssProperties.getInteger(str + ".max", 10);
			CONNECTION_POOL = DriverTransactionalPool.get(driver, connectString, user, password, controlQuery, maximo);
			traceError("DriverParameters=CONNECTION_POOL " + CONNECTION_POOL);
			this.source = connectString;
		}
		// Obtengo la conexion por defecto, que deberia estar definida en los parametros de inicio del web.xml
		else {
			String defaultDs = gssProperties.getProperty("serest4j.defaultds");
			if( defaultDs != null  &&  defaultDs.trim().length() > 0 ) {
				CONNECTION_POOL = new DSTransactionalPool(defaultDs);
				traceError("serest4j.defaultds=CONNECTION_POOL (" + defaultDs + ") " + CONNECTION_POOL);
				this.source = defaultDs;
			}
			else {
				CONNECTION_POOL = null;
				this.source = "NO DATA SOURCE";
			}
		}
		if( CONNECTION_POOL == null ) {
			traceError("CONNECTION_POOL no configurado");
		}
	}

	/**
	 * Le pasamos directamente las url y parametros de conectividad JDBC
	 * @param driver
	 * @param connectString
	 * @param user
	 * @param password
	 * @param maxConexiones maximo de conexiones simulataneas para este pool de conexiones
	 * @param loggerName
	 * @param userLogger
	 * @throws SQLException
	 */
	protected void initTransaction(String driver, String connectString, String user, String password, String controlQuery, int maxConexiones,
			String contexto, String loggerName, Logger userLogger) throws SQLException {
		this.loggerName = loggerName;
		this.userLogger = userLogger;
		this.error = FileLogger.getLogger(null);
		this.debug = this.error.isDebugEnabled() ? this.error : null;

		this.error.trace("Creado " + this);
		// Obtengo la conexion JDBC
		CONNECTION_POOL = DriverTransactionalPool.get(driver, connectString, user, password, controlQuery, maxConexiones);
		if( CONNECTION_POOL == null ) {
			traceError("CONNECTION_POOL no configurado");
			this.source = "";
		}
		else {
			traceError("DriverParameters=CONNECTION_POOL " + CONNECTION_POOL);
			this.source = connectString;
		}
	}

	/**
	 * @param dataSource El nombre del DataSource que me generará las conexiones a la base de datos.
	 * @param loggerName Nos sirve para definir el nombre del fichero de Loggers. Si es null toma el nombre de la clase anterior por defecto.
	 * @throws SQLException
	 * @throws NamingException
	 */
	protected void initTransaction(String dataSource, String contexto, String loggerName, Logger userLogger) throws SQLException, NamingException {
		this.loggerName = loggerName;
		this.userLogger = userLogger;
		this.error = FileLogger.getLogger(contexto, this.loggerName);
		this.debug = this.error.isDebugEnabled() ? this.error : null;
		this.error.trace("Creado " + this);
		// Obtengo el data source, si la clase contiene la anotacion DataSource
		this.source = dataSource == null ? "" : dataSource.trim();
		if( this.source.length() > 0 )
			CONNECTION_POOL = new DSTransactionalPool(dataSource);
		if( CONNECTION_POOL == null ) {
			traceError("CONNECTION_POOL no configurado");
		}
		else {
			traceError("DataSource=CONNECTION_POOL (" + this.source + ") " + CONNECTION_POOL);
		}
	}

	protected Logger getUserLogger() {
		return userLogger;
	}

	private void traceError(String msg) {
		if( error != null  &&  error.isTraceEnabled() ) {
			error.trace(msg);
		}
	}

	private void logError(String msg, Throwable th) {
		if( debug != null ) {
			if( th != null )
				debug.error(msg, th);
			else
				debug.error(msg);
		}
		else {
			defaultLogger.error(msg, th);
		}
		if( userLogger != null ) {
			if( th != null )
				userLogger.error(msg, th);
			else
				userLogger.error(msg);
		}
	}
	
	private void logDebug(String msg) {
		if( debug != null  &&  debug.isDebugEnabled() ) {
			debug.debug(msg);
		}
		if( userLogger != null  &&  debug.isDebugEnabled() ) {
			userLogger.debug(msg);
		}
	}

	protected synchronized boolean hashConnectionPool() {
		if( CONNECTION_POOL == null  ||  CONNECTION_POOL instanceof NullTransactionalPool )
			return false;
		else
			return true;
	}

	protected String getSourceName() {
		return hashConnectionPool() ? source : null;
	}

	protected String getSourcePoolId() {
		return hashConnectionPool() ? CONNECTION_POOL.getId() : null;
	}

	protected TMTransactionalLogger initLog(String nombre) throws SQLException, NamingException {
		StringBuilder sb = debug == null ? null : new StringBuilder();
		return hashConnectionPool() ? new DefaultTransactionalLogger(null, source, nombre, CONNECTION_POOL.next(), sb, debug) : null;
	}

	protected synchronized TMTransactionalLogger initLog(String usuario, String nombre) throws SQLException, NamingException {
		StringBuilder sb = debug == null ? null : new StringBuilder();
		return hashConnectionPool() ? new DefaultTransactionalLogger(usuario, source, nombre, CONNECTION_POOL.next(), sb, debug) : null;
	}

	protected void throwsRollback(String name, Object... error) throws SQLException {
		throw new SQLException("Rollback en " + name + " " + Arrays.toString(error));
	}

	private void rollback(TMTransactionalLogger log) {
		if( log.getConexion() != null ) {
			CONNECTION_POOL.set(log.getConexion(), true);	
		}
	}

	private void commit(TMTransactionalLogger log) {
		if( log.getConexion() != null ) {
			CONNECTION_POOL.set(log.getConexion(), false);	
		}
	}

	protected void printLog(TMTransactionalLogger log) {
		printLog(log, null);
	}

	@SuppressWarnings("serial")
	private class NotUsedThrowable extends Throwable {

		private NotUsedThrowable(Throwable th) {
			super(th);
		}
	}

	protected Throwable getNotUsedThrowable(Throwable th) {
		return th == null ? null : new NotUsedThrowable(th); 
	}

	protected void printLog(TMTransactionalLogger log, Throwable error) {
		if( log == null  ||  log.isClosed() ) {
			return;
		}
		DBTimeStatistics.addEstadisticas(log.duracion(), error != null);
		log.close();
		if( error != null ) {
			if( error instanceof NotUsedThrowable ) {
				logError(log.toString() +"\n -- " + error.getCause(), null);
			}
			else {
				logError(log.toString(), error);	
			}
			rollback(log);
		}
		else {
			logDebug(log.toString());
			commit(log);
		}
	}
}
