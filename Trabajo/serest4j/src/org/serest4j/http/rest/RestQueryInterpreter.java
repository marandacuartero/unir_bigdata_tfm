package org.serest4j.http.rest;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.serest4j.http.RequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class RestQueryInterpreter {

	private int maxSizePath;
	private RestServicesMapping restServicesMapping;

	public RestQueryInterpreter(RestServicesMapping restServicesMapping, int maxSizePath) {
		this.maxSizePath = maxSizePath;
		this.restServicesMapping = restServicesMapping;
	}

	public RestQueryInfo convertir(String prefijo, HttpServletRequest request, Logger debug) throws FileNotFoundException {
		ArrayList<String> argumentos = new ArrayList<String>();
		String queryString = request.getQueryString();

		StringBuilder sbRutaCompleta = new StringBuilder();
		if( request.getServletPath() != null )
			sbRutaCompleta.append(request.getServletPath().trim());
		if( request.getPathInfo() != null )
			sbRutaCompleta.append(request.getPathInfo().trim());

		if( debug != null ) {
			StringBuilder sb = new StringBuilder("RutaCompleta Original >> " + sbRutaCompleta.toString());
			sb.append("\nPath Info:").append(request.getPathInfo());
			sb.append("\nServlet Path:").append(request.getServletPath());
			sb.append("\nHeader:\n");
			for( Enumeration<String> ee = request.getHeaderNames(); ee.hasMoreElements(); ) {
				String key = ee.nextElement();
				String valor = request.getHeader(key);
				sb.append('\n').append('\t').append(key).append('=').append(valor);
			}
			debug.debug(sb.toString());
		}
		if( sbRutaCompleta.length() > 0 ) {
			if( prefijo != null ) {
				while( sbRutaCompleta.indexOf(prefijo) == 0 ) {
					sbRutaCompleta.delete(0, prefijo.length());	
				}
			}
			if( debug != null ) {
				debug.debug("RutaCompleta Traducida >> " + sbRutaCompleta);
			}
			int nSb = sbRutaCompleta.length();
			int nmax = maxSizePath;
			int prev = 0;
			for( int i=0; i<nSb; i++ ) {
				if( sbRutaCompleta.charAt(i) == '/' ) {
					if( prev < i ) {
						argumentos.add(sbRutaCompleta.substring(prev, i));
						if( argumentos.size() > nmax ) {
							argumentos.clear();
							return null;
						}
					}
					prev = i + 1;
				}
			}
			if( prev < sbRutaCompleta.length() ) {
				argumentos.add(sbRutaCompleta.substring(prev));
			}
			Enumeration<String> enumeration = request.getParameterNames();
			TreeSet<String> ts = new TreeSet<String>();
			String forwardService = null;
			String forwardServiceType = null;
			String contentType = null;
			String contentName = null;
			String idSesion = null;
			while( enumeration.hasMoreElements() ) {
				String key = enumeration.nextElement();
				if( key.trim().equalsIgnoreCase(RequestAttributes.FORWARD_NAME) ) {
					forwardService = normalizar(request.getParameter(key));
				}
				else if( key.trim().equalsIgnoreCase(RequestAttributes.FORWARD_TYPE) ) {
					forwardServiceType = normalizar(request.getParameter(key));
				}
				else if( key.trim().equalsIgnoreCase(RequestAttributes.CONTENT_TYPE) ) {
					contentType = normalizar(request.getParameter(key));
				}
				else if( key.trim().equalsIgnoreCase(RequestAttributes.CONTENT_NAME) ) {
					contentName = normalizar(request.getParameter(key));
				}
				else if( key.trim().equalsIgnoreCase(RequestAttributes.COOKIE_ID) ) {
					idSesion = normalizar(request.getParameter(key));
				}
				else {
					ts.add(key);
				}
			}
			for( String strKey : ts ) {
				String[] strValues = request.getParameterMap().get(strKey);
				if( strValues != null  &&  strValues.length == 1 ) {
					argumentos.add(strValues[0]);
					if( debug != null ) {
						debug.debug("Parametro " + strKey + "=" + strValues[0]);
					}
				}
				else if( strValues != null  &&  strValues.length > 0 ) {
					StringBuilder sbargumentos = new StringBuilder();
					int nn = sbargumentos.length();
					for( String strVal : strValues ) {
						if( strVal != null  &&  strVal.length() > 0 ) {
							sbargumentos.append(strVal);
							nn = sbargumentos.length();
							sbargumentos.append(',');
						}
					}
					sbargumentos.setLength(nn);
					if( sbargumentos.length() > 0 ) {
						argumentos.add(sbargumentos.toString());
					}
					if( debug != null ) {
						debug.debug("Parametro " + strKey + "=" + sbargumentos);
					}
				}
				if( argumentos.size() > nmax ) {
					ts.clear();
					argumentos.clear();
					return null;
				}
			}
			ts.clear();
			if( debug != null ) {
				debug.debug("Servicios >> " + argumentos.toString());
				debug.debug("Query String >> " + queryString);
				debug.debug("Contexto >> " + request.getContextPath());
				debug.debug("Argumentos >> " + argumentos);
			}
			if( argumentos.size() > 0 ) {
				String nombreServicio = null;
				String servicioTraducido = null;
				String nombreServicioForwardProperties = null;
				String nombreServicioForwardTypeProperties = "forward";
				String nombreServicioContentTypeProperties = null;
				String nombreServicioContentNameProperties = null;
				boolean validado = false;
				nmax = Math.min(5, argumentos.size());
				for( int i=0; !validado  &&  i<nmax; i++ ) {
					nombreServicio = argumentos.remove(0).toLowerCase();
					servicioTraducido = restServicesMapping.getService(nombreServicio);
					if( servicioTraducido != null ) {
						nombreServicioForwardProperties = normalizar(restServicesMapping.getService(nombreServicio + ".forward"));
						if( nombreServicioForwardProperties == null ) {
							nombreServicioForwardProperties = normalizar(restServicesMapping.getService(nombreServicio + ".redirect"));
							nombreServicioForwardTypeProperties = "redirect";
							if( nombreServicioForwardProperties == null ) {
								nombreServicioForwardProperties = normalizar(restServicesMapping.getService(nombreServicio + ".include"));
								nombreServicioForwardTypeProperties = "include";
							}
						}
						nombreServicioContentTypeProperties = normalizar(restServicesMapping.getService(nombreServicio + ".content.type"));
						nombreServicioContentNameProperties = normalizar(restServicesMapping.getService(nombreServicio + ".content.name"));
						// intento construir los argumentos en caso de tener parametrizados los parametros
						ArrayList<String> alParametros = null;
						boolean seguir = true;
						for( int numeroParametro=0; seguir; numeroParametro++ ) {
							String nombreParametro = restServicesMapping.getService(nombreServicio + ".p" + numeroParametro);
							if( nombreParametro != null ) {
								String valueParametro = request.getParameter(nombreParametro);
								if( alParametros == null ) {
									alParametros = new ArrayList<String>();
								}
								if( valueParametro != null ) {
									alParametros.add(valueParametro.toString());
								}
								else {
									alParametros.add("null");
								}
							}
							else {
								seguir = false;
							}
						}
						validado = true;
						if( alParametros != null ) {
							argumentos.clear();
							argumentos.addAll(alParametros);
						}
					}
				}
				if( validado ) {
					RestQueryInfo conversorQueryHeader = new RestQueryInfo();
					conversorQueryHeader.setServicioSistema(servicioTraducido);
					forwardService = UrlCleaner.clean(request, forwardService == null ? nombreServicioForwardProperties : forwardService);
					forwardServiceType = forwardServiceType == null ? nombreServicioForwardTypeProperties : forwardServiceType;
					conversorQueryHeader.setForward(forwardService, forwardServiceType);
					if( forwardService == null ) {
						contentType = contentType == null ? nombreServicioContentTypeProperties : contentType;
						contentName = contentName == null ? nombreServicioContentNameProperties : contentName;
						conversorQueryHeader.setContent(contentType, contentName);
					}
					if( idSesion != null ) {
						argumentos.add(0, idSesion);
					}
					conversorQueryHeader.setArgumentos(argumentos);
					return conversorQueryHeader;
				}
				else {
					argumentos.clear();
				}
			}
		}
		return null;
	}

	private String normalizar(String servicio) {
		if( servicio != null ) {
			servicio = servicio.trim();
			if( servicio.length() > 0 ) {
				return servicio;
			}
		}
		return null;
	}
}
