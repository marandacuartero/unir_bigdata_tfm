package org.serest4j.context;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.db.DefaultTransactionalLogger;
import org.serest4j.db.TMTransactionalLogger;
import org.serest4j.db.TransactionalBaseContainer;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.idserver.policy.CredencialsInterface;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Permite inyectar un contexto transaccional sobre un controlador
 * 
 * Este contexto realiza la inyeccion de la conexion proveniente del pool de conexiones,
 * y gestiona el commit y el rollback.
 * Tambien permite la inyeccion de parametros y atributos del request de la peticion http, asi como
 * de la sesion de usuario en curso, y el control de la concurrencia entre el iterator que se va generando
 * en el servidor y el que se va leyendo desde el cliente.
 * 
 * @author Maranda
 * 
 * @see TransactionalBaseContainer
 *
 */
public class TMContext extends TransactionalBaseContainer {

	private HttpServletRequest request;
	private ServletContext context;
	private TMTransactionalLogger transaccionLog;
	private Connection conexion; 
	private String idSesion;
	private String userCode;
	private Object userSesion;
	private Properties proxyProperties;
	private PropertiesLoader propertiesLoader;
	private CredencialsInterface[] credencialesUsuario;
	private BufferDataProvider output;
	private TMContext[] proxyContextos = new TMContext[0];
	private final Class<?> controlador;
	private TMContext proxyPadre = null;

	private TMContext(PropertiesLoader gssProperties, Class<?> controlador, Logger userLogger) throws SQLException, NamingException {
		super();
		initTransaction(gssProperties, controlador,  userLogger);
		this.propertiesLoader = gssProperties;
		this.controlador = controlador;
		this.proxyPadre = null;
	}

	/**
	 * Realiza la construccion de un nuevo contexto
	 * @param controlador La clase que representa al controlador sobre el que queremos realizar la inyeccion de codigo. Se utiliza para la generacion de unos loggers
	 * particulares a este controlador.
	 * @param request El request asociado a la peticion http. 
	 * @throws SQLException Se lanza si la conexion a base de datos no existe o no se ha podido generar correctamente.
	 * @throws NamingException Se lanza si el JNDI que representa la conexion a la base de datos, no existe o no tiene ninguna referencia sociada.
	 */
	protected TMContext(PropertiesLoader gssProperties, Class<?> controlador, HttpServletRequest request, Logger userLogger) throws SQLException, NamingException {
		this(gssProperties, controlador, userLogger);
		this.request = request;
		this.proxyProperties = gssProperties.clone();
		if( request != null ) {
			this.context = request.getServletContext();
			this.idSesion = (String)(request.getAttribute(RequestAttributes.ID_SESION));
			this.userCode = (String)(request.getAttribute(RequestAttributes.USER_CODE));
			this.userSesion = request.getAttribute(RequestAttributes.USER_SESION);
			this.credencialesUsuario = (CredencialsInterface[])(request.getAttribute(RequestAttributes.USER_CREDENTIALS));
		}
	}

	/**
	 * Construye un nuevo contexto, tomando como patron de referencia un contexto ya existente, de donde puede
	 * extraer los datos de sesion
	 * @param controlador
	 * @param loggerName
	 * @param proxyContextoOrigen
	 * @throws SQLException
	 * @throws NamingException
	 */
	protected TMContext(PropertiesLoader gssProperties, Class<?> controlador, TMContext proxyContextoOrigen) throws SQLException, NamingException {
		this(gssProperties, controlador, proxyContextoOrigen.getUserLogger());
		this.proxyProperties = gssProperties.clone();
		this.request = proxyContextoOrigen.request;
		this.context = proxyContextoOrigen.context;
		this.idSesion = proxyContextoOrigen.idSesion;
		this.userCode = proxyContextoOrigen.userCode;
		this.userSesion = proxyContextoOrigen.userSesion;
		this.credencialesUsuario = proxyContextoOrigen.credencialesUsuario;
		this.proxyPadre = proxyContextoOrigen.getProxyPadre();
	}

	public void setAttribute(String key, Object value) {
		HttpServletRequest httpServletRequest = this.request;
		if( httpServletRequest != null ) {
			httpServletRequest.setAttribute(key, value);
		}
	}

	PropertiesLoader getPropertiesLoader() {
		return propertiesLoader;
	}

	boolean addProxyToParent() throws SQLException {
		TMContext proxyContextoOrigen = getProxyPadre();
		if( proxyContextoOrigen != this ) {
			synchronized (proxyContextoOrigen) {
				TMContext[] pco = proxyContextoOrigen.proxyContextos;
				if( pco != null ) {
					for( TMContext _proxy : pco ) {
						if( _proxy == this ) {
							return false;
						}
					}
					pco = Arrays.copyOf(pco, pco.length + 1);
					pco[pco.length - 1] = this;
					proxyContextoOrigen.proxyContextos = pco;
					return true;
				}
			}
		}
		return false;
	}

	private TMContext getProxyPadre() {
		if( this.proxyPadre == null )
			return this;
		else
			return this.proxyPadre.getProxyPadre();
	}

	TMContext searchProxyEquivalente() {
		TMContext proxyContextoOrigen = getProxyPadre();
		if( proxyContextoOrigen != this ) {
			String idConexion = getSourcePoolId();
			synchronized (proxyContextoOrigen) {
				if( idConexion != null ) {
					if( idConexion.equalsIgnoreCase(proxyContextoOrigen.getSourcePoolId()) ) {
						return proxyContextoOrigen;
					}
					TMContext[] pco = proxyContextoOrigen.proxyContextos;
					if( pco != null  &&  pco.length > 0 ) {
						for( TMContext _proxy : pco ) {
							if( _proxy != null  &&  idConexion.equalsIgnoreCase(_proxy.getSourcePoolId()) ) {
								// tengo una conexion equivalente, asi que la tomo como tal
								if( _proxy.getConexion() != null ) {
									return _proxy;
								}
							}
						}
					}
				}
			}
		}
		return this;
	}

	/**
	 * Inicializa los logs y la conexion a base de datos asociada a la transaccion de este contexto.
	 * 
	 * @param nombreServicio
	 */
	protected void initDB(String nombreServicio) {
		if( transaccionLog == null ) {
			if( conexion != null ) {
				transaccionLog = new DefaultTransactionalLogger(userCode, getSourceName(), nombreServicio, conexion, new StringBuilder(), debug);
			}
			else {
				try {
					transaccionLog = initLog(userCode, nombreServicio);
					if( transaccionLog != null ) {
						conexion = transaccionLog.getConexion();
					}
				} catch(Exception e) {
					if( error != null ) {
						error.error(controlador, e);
					}
				}
			}
		}
	}

	public void setOutputNMaxBuffer(int nMaxBuffer) throws IOException {
		output.setNMaxBuffer(nMaxBuffer);
	}

	public void setOutputSize(int size) throws IOException {
		output.setSize(size);
	}

	public void setOutputContent(String contentType, String contentName)
			throws IOException {
		output.setContent(contentType, contentName);
	}

	public BufferDataProvider sendOutput(Object obj) throws IOException {
		return output.send(obj);
	}

	public BufferDataProvider getOutput() {
		return output;
	}

	public void setOutput(BufferDataProvider output) {
		this.output = output;
	}

	public String getProxyProperty(String key) {
		String value = null;
		if( proxyProperties != null ) {
			Object obj = proxyProperties.get(key);
			if( obj != null ) {
				value = obj.toString();
			}
		}
		return value;
	}

	public Logger getLogger() {
		if( super.debug != null ) {
			return super.debug;
		}
		else if( super.error != null ) {
			return super.error;
		}
		else {
			return FileLogger.getLogger(context == null ? null : context.getContextPath());
		}
	}

	public TMTransactionalLogger getTransaccionLog() {
		return transaccionLog;
	}

	public Connection getConexion() {
		return conexion;
	}

	public HttpServletRequest getRequest() {
		return request;
	}

	public ServletContext getContext() {
		return context;
	}

	public String getIdSesion() {
		return idSesion;
	}

	public String getUserCode() {
		return userCode;
	}

	public Object getUserSesion() {
		return userSesion;
	}

	public CredencialsInterface[] getCredencialesUsuario() {
		return credencialesUsuario;
	}

	private final AtomicBoolean cleared = new AtomicBoolean(false);

	public void clear(Throwable th) {
		if( cleared.compareAndSet(false, true) ) {
			request = null;
			userSesion = null;
			userCode = null;
			context = null;
			if( transaccionLog != null ) {
				if( th != null  &&  th  instanceof TMReturnContextException ) {
					TMReturnContextException rcte = (TMReturnContextException)th;
					// este tipo de excepcion se trata de manera distinta, se transmite desde el contexto
					// pero no provoca el rollback de las transacciones a no ser que internamente
					// la propia excepcion contenga una excepcion asociada al proceso
					transaccionLog.println(rcte.getMinimaTraza(1));
					printLog(transaccionLog, rcte.getCause());
				}
				else {
					printLog(transaccionLog, th);	
				}
				conexion = null;
			}
			else if( getLogger() != null ) {
				Logger logger = getLogger();
				if( th != null  &&  th  instanceof TMReturnContextException ) {
					TMReturnContextException rcte = (TMReturnContextException)th;
					// este tipo de excepcion se trata de manera distinta, se transmite desde el contexto
					// pero no provoca el rollback de las transacciones a no ser que internamente
					// la propia excepcion contenga una excepcion asociada al proceso
					if( th.getCause() != null ) {
						logger.error(rcte + " >> " + rcte.getRetorno(), rcte.getCause());
					}
					else {
						logger.debug(rcte + " >> " + rcte.getRetorno(), rcte.getCause());
					}
				}
				else if( th != null ) {
					logger.error("ProxyContexto.clear", th);
				}
				else if( super.debug != null ) {
					debug.trace("ProxyContexto.clear");
				}
			}
			else if( th != null ) {
				if( th instanceof TMReturnContextException ) {
					if( th.getCause() != null ) {
						System.err.println(((TMReturnContextException)th).getMinimaTraza(1));
						th.getCause().printStackTrace();
					}
					else {
						System.err.println(((TMReturnContextException)th).getMinimaTraza(5));
					}
				}
				else
					th.printStackTrace();
			}
			if( conexion != null ) {
				try { conexion.close(); } catch (Exception e) {}
			}
			transaccionLog = null;
			conexion = null;
			if( output != null ) {
				output.close();
			}
			output = null;
			if( proxyProperties != null ) {
				proxyProperties.clear();
			}
			proxyProperties = null;
			if( proxyContextos != null ) {
				for( TMContext proxy : proxyContextos ) {
					if( proxy != null ) {
						if( th != null  &&  th instanceof TMReturnContextException ) {
							proxy.clear(super.getNotUsedThrowable(th.getCause()));	
						}
						else if( th != null ) {
							proxy.clear(super.getNotUsedThrowable(th));
						}
						else {
							proxy.clear(null);
						}
					}
				}
				Arrays.fill(proxyContextos, null);
				proxyContextos = null;
			}
			proxyPadre = null;
		}
	}
}
