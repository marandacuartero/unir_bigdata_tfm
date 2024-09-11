package org.serest4j.http.rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.log4j.Logger;
import org.serest4j.annotation.db.TMTableName;
import org.serest4j.annotation.rest.TMContentName;
import org.serest4j.annotation.rest.TMContentType;
import org.serest4j.annotation.rest.TMPageForward;
import org.serest4j.annotation.rest.TMRest;
import org.serest4j.annotation.rest.TMRestAlias;
import org.serest4j.annotation.rest.TMRestParameters;
import org.serest4j.annotation.service.TMBasicController;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.db.TMRelationProcessor;

public class RestConfigurationLoader {

	public static void loadInitParameters(Logger logger, Class<?> clase, RestServicesMapping restServicesMapping) {
		_loadInitParameters(logger, clase, clase, restServicesMapping);
	}
	
	private static void _loadInitParameters(Logger logger, Class<?> claseActual, Class<?> claseOrigen, RestServicesMapping restServicesMapping) {
		if( claseActual != null ) {
			boolean esControlador = claseActual.isInterface();
			if( !esControlador ) {
				Annotation[] anotaciones = claseActual.getAnnotations();
				if( anotaciones != null ) {
					for( Annotation a : anotaciones ) {
						if( a instanceof TMInjectableContext ) {
							esControlador = true;
						}
						else if( a instanceof TMBasicController ) {
							esControlador = true;
						}
					}
				}
			}
			if( esControlador ) {
				buildClassParameters(logger, claseActual, claseOrigen, restServicesMapping);
				Class<?> clasePadre = claseActual.getSuperclass();
				if( clasePadre != null  &&  clasePadre != Object.class ) {
					_loadInitParameters(logger, clasePadre, claseOrigen, restServicesMapping);
				}
				Class<?>[] interfaces = claseActual.getInterfaces();
				for( Class<?> interfaz : interfaces ) {
					_loadInitParameters(logger, interfaz, claseOrigen, restServicesMapping);
				}
			}
		}
	}

	private static void buildClassParameters(Logger logger, Class<?> claseActual, Class<?> claseOrigen, RestServicesMapping restServicesMapping) {
		Method[] metodos = claseActual.getMethods();
		for( Method m : metodos ) {
			if( m.getDeclaringClass().equals(claseActual) ) {
				buildMethodParameters(logger, m, claseOrigen, restServicesMapping);
			}
		}
		if( TMRelationProcessor.class.isAssignableFrom(claseActual) ) {
			TMTableName tabla = claseActual.getAnnotation(TMTableName.class);
			if( tabla != null ) {
				String tableAlias = tabla.alias().trim();
				if( tableAlias.length() <= 0  &&  tabla.crud() ) {
					tableAlias = tabla.name().toLowerCase();
				}
				if( tableAlias.length() > 0 ) {
					for( String nmethod : TMRelationProcessor.CRUD_METHODS ) {
						String nombreCompleto = claseOrigen.getName() + "." + nmethod;
						String alias = tableAlias + "." + nmethod.toLowerCase();
						buildCRUDMethodParameters(logger, nombreCompleto, alias, restServicesMapping);
					}
				}
			}
		}
	}

	public static void buildMethodParameters(Logger logger, Method metodoActual, Class<?> claseOrigen, RestServicesMapping restServicesMapping) {
		TMRestAlias proxyAlias1 = metodoActual.getAnnotation(TMRestAlias.class);
		TMRest proxyAlias2 = metodoActual.getAnnotation(TMRest.class);
		if( proxyAlias1 != null  ||  proxyAlias2 != null ) {
			String nombreCompleto = claseOrigen.getName() + "." + metodoActual.getName();
			String alias = null;
			if( proxyAlias1 != null ) {
				alias = proxyAlias1.value().trim();
			}
			if( alias == null  ||  alias.length() <= 0 ) {
				alias = metodoActual.getName().toLowerCase();
			}
			String mapeoServicio = restServicesMapping.addNextService(alias, nombreCompleto);
			buildDetailMethod(logger, mapeoServicio, nombreCompleto, metodoActual, restServicesMapping);
		}
	}

	private static void buildDetailMethod(Logger logger, String mapeoServicio, String nombreCompleto, Method metodoActual, RestServicesMapping restServicesMapping) {
		if( mapeoServicio != null ) {
			if( logger != null  &&  logger.isDebugEnabled() ) {
				logger.debug(restServicesMapping.getId() + " ProxyAlias " + mapeoServicio + " >> " + nombreCompleto);	
			}
			TMRestParameters proxyParameters = metodoActual.getAnnotation(TMRestParameters.class);
			if( proxyParameters != null ) {
				String[] listaParametros = proxyParameters.value();
				int numeroParametro = 0;
				for( String strParam : listaParametros ) {
					String nombreParametro = restServicesMapping.addNextService(mapeoServicio + ".p" + numeroParametro, strParam);
					if( nombreParametro != null ) {
						if( logger != null  &&  logger.isDebugEnabled() ) {
							logger.debug(restServicesMapping.getId() + " ProxyParameters " + nombreParametro + " >> " + strParam);
						}
						numeroParametro++;
					}
				}
			}
			TMPageForward proxyForward = metodoActual.getAnnotation(TMPageForward.class);
			if( proxyForward != null ) {
				String pfwd = restServicesMapping.addNextService(mapeoServicio + "." + proxyForward.mode(), proxyForward.value());
				if( pfwd != null ) {
					if( logger != null  &&  logger.isDebugEnabled() ) {
						logger.debug(restServicesMapping.getId() + " ProxyForward " + mapeoServicio + "." + proxyForward.mode() + " >> " + proxyForward.value());
					}
					proxyForward = null;
				}
			}
			if( proxyForward == null ) {
				buildContent(logger, mapeoServicio, metodoActual, restServicesMapping);
			}
		}
	}

	private static void buildContent(Logger logger, String mapeoServicio, Method metodoActual, RestServicesMapping restServicesMapping) {
		String texto = buildContentType(logger, mapeoServicio, metodoActual, restServicesMapping);
		if( texto != null ) {
			TMContentName tmContentName = metodoActual.getAnnotation(TMContentName.class);
			if( tmContentName != null ) {
				texto = restServicesMapping.addNextService(mapeoServicio + ".content.name", tmContentName.value());
				if( texto != null ) {
					if( logger != null  &&  logger.isDebugEnabled() ) {
						logger.debug(restServicesMapping.getId() + " Content Name " + mapeoServicio + ".content.name >> " + tmContentName.value());	
					}
				}
			}
		}
	}

	public static String buildContentType(Logger logger, String mapeoServicio, Method metodoActual, RestServicesMapping restServicesMapping) {
		TMContentType tmContentType = metodoActual.getAnnotation(TMContentType.class);
		if( tmContentType != null ) {
			String texto = restServicesMapping.addNextService(mapeoServicio + ".content.type", tmContentType.value());
			if( texto != null ) {
				if( logger != null  &&  logger.isDebugEnabled() ) {
					logger.debug(restServicesMapping.getId() + " ContentType " + mapeoServicio + ".content.type >> " + tmContentType.value());	
				}
				return tmContentType.value();
			}
		}
		return null;
	}

	private static void buildCRUDMethodParameters(Logger debug, String nombreCompleto, String alias, RestServicesMapping restServicesMapping) {
		String mapeoServicio = restServicesMapping.addNextService(alias, nombreCompleto);
		if( mapeoServicio != null ) {
			if( debug != null ) {
				debug.debug(restServicesMapping.getId() + " CRUD ProxyAlias " + mapeoServicio + " >> " + nombreCompleto);	
			}
		}
	}
}
