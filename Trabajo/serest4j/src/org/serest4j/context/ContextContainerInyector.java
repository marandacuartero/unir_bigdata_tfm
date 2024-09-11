package org.serest4j.context;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.serest4j.annotation.service.TMBasicController;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.db.TMRelationProcessor;
import org.serest4j.db.TMTransactionalLogger;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Libreria que realiza inyeccion de contexto
 * 
 * @see TMInjectableContext
 * @see ControllerFactory
 * @see TMContext
 * @see TMTransactionalLogger
 * 
 * @author Maranda
 *
 */
public class ContextContainerInyector {

	private static ContextContainerInyector pci = null;

	private Logger debug = null;

	ContextContainerInyector(Logger logger) {
		if( logger != null  &&  logger.isDebugEnabled() ) {
			this.debug = logger;
		}
	}

	private static ContextContainerInyector get(Logger logger) {
		if( pci == null ) {
			pci = new ContextContainerInyector(logger);
		}
		return pci;
	}

	/**
	 * Permite propagar el contexto asociado a la transaccion principal, de forma que
	 * se genera una invocacion vinculada a esta transaccion. Si cualquiera de ellas falla
	 * eso provocara en rollback en todas ellas. 
	 * @param <T>
	 * @param controlador
	 * @param proxyContextoOrigen
	 * @return
	 * @throws SQLException
	 */
	public static <T> T propaga(T controlador, TMContext proxyContextoOrigen) throws SQLException {
		ContextContainerInyector _pci = get(proxyContextoOrigen.getLogger());
		TMTransactionalLogger tl = proxyContextoOrigen.getTransaccionLog();
		String nombreServicio = null;
		if( tl != null ) {
			nombreServicio = tl.getName();
		}
		PropertiesLoader propertiesLoader = proxyContextoOrigen.getPropertiesLoader();
		TMContext nuevoProxyContexto = _pci.inyectaContexto(controlador, nombreServicio, propertiesLoader, null, proxyContextoOrigen, null);
		if( nuevoProxyContexto != null  &&  nuevoProxyContexto.addProxyToParent() ) {
			nuevoProxyContexto.setOutput(proxyContextoOrigen.getOutput());
		}
		return controlador;
	}

	/**
	 * Permite invocar al servicio de un  controlador realizando inyeccion de contexto, de manera interna al servidor, o sea, sin necesidad
	 * de que la invocacion provenga desde una solicitud http.
	 * Esto tambien permite que se pueda invocar un controlador con su transaccion independiente, que no influya
	 * en el resto del proceso.
	 * 
	 * @param <T> Objeto que representa al controlador sobre el que se realiza la inyeccion de contexto 
	 * @param <R> El retorno del servicio invocado
	 * @param transactionContexto
	 * @param loggerName Nombre del logger de volcado de trazas
	 * @param nombreServicio Nombre del servicio invocado
	 * @return El objeto <R> que retorna el servicio invocado
	 * 
	 * @see TMAbstractContextContainer
	 * 
	 */
//	public static <T, R> R inyecta(TMAbstractContextContainer<T, R> transactionContexto, String loggerName, String nombreServicio) {
//		ContextContainerInyector _pci = get();
//		TMContext proxyContexto = _pci.inyectaContexto(transactionContexto.getT(), loggerName, nombreServicio, null, null, null);
//		return transactionContexto.execute(proxyContexto);
//	}
//
//	
//	public static <T, R> R inyecta(TMAbstractContextContainer<T, R> transactionContexto) {
//		String loggerName = transactionContexto.getT().getClass().getSimpleName();
//		return inyecta(transactionContexto, loggerName, loggerName);
//	}
//
	TMContext inyectaContexto(Object obj, String nombreServicio, PropertiesLoader propertiesLoader, HttpServletRequest request, Logger userLogger) {
		return inyectaContexto(obj, nombreServicio, propertiesLoader, request, null, userLogger);
	}

	/**
	 * Inyecta el contexto a este controlador
	 * 
	 * @param obj El controlador al que queremos inyectar el contexto
	 * @param nombreServicio El nombre del servicio que queremos invocar
	 * @param request La request asociada a la peticion http
	 * @return El proxy contexto inyectado a este controlador
	 */
	private TMContext inyectaContexto(Object obj, String nombreServicio, PropertiesLoader propertiesLoader, HttpServletRequest request, TMContext proxyContextoOrigen, Logger userLogger) {
		TMContext proxyContexto = null;
		if( obj != null  &&  obj instanceof TMRelationProcessor ) {
			proxyContexto = _inyectaTMRelationProcessor(obj, nombreServicio, propertiesLoader, request, proxyContextoOrigen, userLogger);
		}
		else if( obj != null  &&  obj.getClass().isAnnotationPresent(TMInjectableContext.class) ) {
			proxyContexto = _inyectaTMInjectableContext(obj, nombreServicio, propertiesLoader, request, proxyContextoOrigen, userLogger);
		}
		else if( obj != null  &&  obj.getClass().isAnnotationPresent(TMBasicController.class) ) {
			_inyectaTMBasicController(obj, nombreServicio, propertiesLoader);
		}
		return proxyContexto;
	}

	private TMContext _inyectaTMRelationProcessor(Object obj, String nombreServicio, PropertiesLoader propertiesLoader, HttpServletRequest request, TMContext proxyContextoOrigen, Logger userLogger) {
		try {
			TMContext proxyContexto = getProxy(obj, null, propertiesLoader, request, proxyContextoOrigen, userLogger);
			proxyContexto.initDB(nombreServicio);
			((TMRelationProcessor<?>)obj).setTMContext(proxyContexto);
			Field[] ff = obj.getClass().getDeclaredFields();
			for( Field f : ff ) {
				if( f != null ) {
					if( Logger.class.isAssignableFrom(f.getType()) ) {
						boolean b = accesible(f, obj);
						if( f.get(obj) == null ) {
							Logger logger = _inyectaLogger(f.getName(), propertiesLoader.getContexto(), obj.getClass());
							if( logger != null ) {
								f.set(obj, logger);
								f.setAccessible(b);
								if( debug != null ) {
									debug.trace("Inyectado Logger " + obj.getClass() + " en " + f.getName() + " de " + obj);
								}
							}
						}
					}
				}
			}
			return proxyContexto;
		} catch (Exception e) {
			if( debug != null ) {
				debug.debug("Error en controlador " + obj, e);
			}
			e.printStackTrace();	
		}
		return null;
	}

	private void fillDeclaredFields(List<Field> campos, Class<?> clase) {
		Field[] ff = clase.getDeclaredFields();
		if( ff != null  &&  ff.length > 0 ) {
			for( Field _f : ff ) {
				campos.add(_f);
			}
		}
		Class<?> clasePadre = clase.getSuperclass();
		if( clasePadre != null  &&  clasePadre != Object.class ) {
			if( clasePadre.isAnnotationPresent(TMInjectableContext.class)  ||  clasePadre.isAnnotationPresent(TMBasicController.class) ) {
				fillDeclaredFields(campos, clasePadre);
			}
		}
	}

	private TMContext _inyectaTMInjectableContext(Object obj, String nombreServicio, PropertiesLoader propertiesLoader, HttpServletRequest request, TMContext proxyContextoOrigen, Logger userLogger) {
		TMContext proxyContexto = null;
		try {
			List<Field> declaredFields = new ArrayList<Field>();
			fillDeclaredFields(declaredFields, obj.getClass());
			for( Field f : declaredFields ) {
				if( f != null ) {
					if( isProxyContextAsignable(f.getType()) ) {
						Object objInyectado = null;
						proxyContexto = getProxy(obj, proxyContexto, propertiesLoader, request, proxyContextoOrigen, userLogger);
						if( isProxyContextDBAsignable(f.getType()) ) {
							proxyContexto.initDB(nombreServicio);
							if( Connection.class.isAssignableFrom(f.getType()) ) {
								objInyectado = proxyContexto.getConexion();
							}
							else if( TMTransactionalLogger.class.isAssignableFrom(f.getType()) ) {
								objInyectado = proxyContexto.getTransaccionLog();
							}
						}
						else {
							if( ServerStaticContext.class.equals(f.getType())  &&  propertiesLoader != null ) {
								objInyectado = ServerStaticContext.get(propertiesLoader.getContexto());
							}
							else if( PropertiesLoader.class.equals(f.getType())  &&  propertiesLoader != null ) {
								objInyectado = propertiesLoader;
							}
							else if( TMContext.class.isAssignableFrom(f.getType()) ) {
								objInyectado = proxyContexto;
							}
							else if( ServletContext.class.isAssignableFrom(f.getType()) ) {
								objInyectado = proxyContexto.getContext();
							}
							else if( HttpServletRequest.class.isAssignableFrom(f.getType()) ) {
								objInyectado = proxyContexto.getRequest();
							}
							else if( Logger.class.isAssignableFrom(f.getType()) ) {
								objInyectado = _inyectaLogger(f.getName(), propertiesLoader.getContexto(), obj.getClass());
							}
						}
						if( objInyectado != null ) {
							boolean b = accesible(f, obj);
							f.set(obj, objInyectado);
							f.setAccessible(b);
							if( debug != null ) {
								debug.trace("Inyectado " + objInyectado.getClass().getName() + " en " + obj);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			if( debug != null ) {
				debug.debug("Error en controlador " + obj, e);
			}
			e.printStackTrace();	
		}
		return proxyContexto;
	}

	private void _inyectaTMBasicController(Object obj, String nombreServicio, PropertiesLoader propertiesLoader) {
		try {
			List<Field> declaredFields = new ArrayList<Field>();
			fillDeclaredFields(declaredFields, obj.getClass());
			for( Field f : declaredFields ) {
				if( f != null ) {
					if( ServerStaticContext.class.equals(f.getType()) ) {
						boolean b = accesible(f, obj);
						if( propertiesLoader != null ) {
							Object obj2 = ServerStaticContext.get(propertiesLoader.getContexto());
							if( obj2 != null ) {
								f.set(obj, obj2);
								f.setAccessible(b);
								if( debug != null ) {
									debug.trace("Inyectado ServerStaticContext " + propertiesLoader.getContexto() + " en " + f.getName() + " de " + obj);
								}
							}
						}
					}
					else if( PropertiesLoader.class.equals(f.getType()) ) {
						boolean b = accesible(f, obj);
						if( f.get(obj) == null  &&  propertiesLoader != null ) {
							f.set(obj, propertiesLoader);
							f.setAccessible(b);
							if( debug != null ) {
								debug.trace("Inyectado PropertiesLoader " + propertiesLoader.getContexto() + " en " + f.getName() + " de " + obj);
							}
						}
					}
					else if( Logger.class.isAssignableFrom(f.getType()) ) {
						boolean b = accesible(f, obj);
						if( f.get(obj) == null ) {
							Logger logger = _inyectaLogger(f.getName(), propertiesLoader.getContexto(), obj.getClass());
							if( logger != null ) {
								f.set(obj, logger);
								f.setAccessible(b);
								if( debug != null ) {
									debug.trace("Inyectado Logger " + obj.getClass() + " en " + f.getName() + " de " + obj);
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			if( debug != null ) {
				debug.debug("Error en controlador " + obj, e);
			}
			e.printStackTrace();	
		}
	}

	private Logger _inyectaLogger(String fieldName, String contexto, Class<?> loggerName) {
		Logger logger = FileLogger.getLogger(contexto, loggerName);
		if( fieldName.equals("trace")  &&  !logger.isEnabledFor(Level.TRACE) ) { logger = null; }
		else if( fieldName.equals("warn")  &&  !logger.isEnabledFor(Level.WARN) ) { logger = null; }
		else if( fieldName.equals("debug")  &&  !logger.isEnabledFor(Level.DEBUG) ) { logger = null; }
		else if( fieldName.equals("info")  &&  !logger.isEnabledFor(Level.INFO) ) { logger = null; }
		return logger;
	}

	private boolean accesible(Field f, Object obj) {
		boolean b = f.canAccess(obj);
		f.setAccessible(true);
		return b;
	}

	private boolean isProxyContextDBAsignable(Class<?> tipo) {
		return Connection.class.isAssignableFrom(tipo)
		||  TMTransactionalLogger.class.isAssignableFrom(tipo);
	}

	private boolean isProxyContextAsignable(Class<?> tipo) {
		return TMContext.class.isAssignableFrom(tipo)
		||  ServletContext.class.isAssignableFrom(tipo)
		||  HttpServletRequest.class.isAssignableFrom(tipo)
		||  Logger.class.isAssignableFrom(tipo)
		||  isProxyContextDBAsignable(tipo)
		||  PropertiesLoader.class.equals(tipo)
		||  ServerStaticContext.class.equals(tipo);
	}

	private TMContext getProxy(Object obj, TMContext proxy, PropertiesLoader propertiesLoader, HttpServletRequest request, TMContext proxyContextoOrigen, Logger userLogger) throws SQLException, NamingException {
		if( proxy == null ) {
			if( proxyContextoOrigen != null ) {
				proxy = new TMContext(propertiesLoader, obj.getClass(), proxyContextoOrigen);
				TMContext proxy2 = proxy.searchProxyEquivalente();
				if( proxy2 != proxy ) {
					proxy.clear(null);
					if( debug != null ) {
						debug.trace("Reutilizando proxy " + proxy2);
					}
					return proxy2;
				}
				else {
					return proxy;
				}
			}
			else {
				// generamos un proxy totalmente nuevo
				proxy = new TMContext(propertiesLoader, obj.getClass(), request, userLogger);
			}
		}
		return proxy;
	}
}
