package org.serest4j.http.rest.js;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.serest4j.common.FileLogger;
import org.serest4j.http.rest.RestServicesMapping;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TMPropertiesFilter implements Filter {

	private RestServicesMapping restServicesMapping = null;

	public void setRestServicesMapping(RestServicesMapping restServicesMapping) {
		this.restServicesMapping = restServicesMapping;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		if( servletRequest instanceof HttpServletRequest ) {
			HttpServletRequest request = (HttpServletRequest)servletRequest;
			HttpServletResponse response = (HttpServletResponse)servletResponse;
			Logger logger = FileLogger.getLogger(request.getContextPath());
			String url = request.getServletPath();
			String urlQuery = request.getQueryString();
			if( url != null  &&  url.endsWith(".js") ) {
				if( logger != null  &&  logger.isDebugEnabled() ) {
					logger.debug("Obteniendo javascript >> " + url);	
				}
				String keyControlador = "serest4j.filter.js.__" + url;
				String nombreControlador = restServicesMapping.getService(keyControlador);
				if( nombreControlador == null ) {
					int io = url.indexOf('/');
					while( io != -1  &&  nombreControlador == null ) {
						url = url.substring(io).trim().toLowerCase();
						keyControlador = "serest4j.filter.js.__" + url;
						nombreControlador = restServicesMapping.getService(keyControlador);
						if( nombreControlador == null ) {
							url = url.substring(1).trim().toLowerCase();
							keyControlador = "serest4j.filter.js.__" + url;
							nombreControlador = restServicesMapping.getService(keyControlador);
						}
						io = url.indexOf('/');
					}
				}
				if( nombreControlador != null ) {
					nombreControlador = nombreControlador.trim();
					String idioma = request.getParameter("locale");
					if( idioma == null  ||  idioma.trim().length() <= 0 )
						idioma = request.getLocale().toString();
					String keyQuery = keyControlador + "[" + idioma + "]" + (urlQuery == null ? "!" : "?" + urlQuery);
					String javaScript = restServicesMapping.getService(keyQuery);
					if( javaScript == null  ||  javaScript.length() <= 0 ) {
						if( logger != null ) {
							logger.debug("Construyendo javascript >> " + url);
						}
						try {
							String nombreJS = url.substring(0, url.length() - 3).trim().replace('/', '_').replace('.', '_');
							while( nombreJS.startsWith("_") ) {
								nombreJS = nombreJS.substring(1).trim();
							}
							while( nombreJS.indexOf("__") != -1 ) {
								nombreJS = nombreJS.replaceAll("__", "_");
							}
							javaScript = generarJavaScript(nombreControlador, nombreJS, request, logger);
						} catch (Throwable th) {
							if( logger != null ) {
								logger.error("Cargando javascrip " + url, th);
							}
						}
						if( javaScript == null ) {
							restServicesMapping.removeService(keyControlador);
							if( logger != null ) {
								logger.debug("No se ha generado el javascript >> " + keyQuery);
							}
						}
						else {
							restServicesMapping.putService(keyQuery, javaScript);
							if( logger != null ) {
								logger.debug("Construido javascript >> " + keyQuery + " >>\n" + javaScript);
							}
						}
					}
					if( javaScript != null  &&  javaScript.length() > 0 ) {
						response.setContentType("text/javascript");
						response.setCharacterEncoding("UTF-8");
						byte[] b = javaScript.getBytes("UTF-8");
						response.getOutputStream().write(b);
						response.getOutputStream().flush();
						response.getOutputStream().close();
						return;
					}
				}
			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private String generarJavaScript(String nombreControlador, String nombreJS, HttpServletRequest request, Logger logger) throws Exception {
		Properties2Js generadorJsProperties = new Properties2Js(logger);
		Enumeration<String> enumeration = request.getParameterNames();
		ArrayList<String> al = new ArrayList<String>();
		Locale locale = request.getLocale();
		while( enumeration.hasMoreElements() ) {
			String key = enumeration.nextElement();
			if( key == null ) {}
			else if( "locale".equalsIgnoreCase(key) ) {
				String language = request.getParameter(key);
				String pais = null;
				int io = language.indexOf('_');
				if( io > 0 ) {
					pais = language.substring(io + 1);
					language = language.substring(0, io);
					locale = new Locale.Builder().setLanguage(language).setRegion(pais).build();
				}
				else {
					locale = new Locale.Builder().setLanguage(language).build();
				}
			}
			else if( "suffix".equalsIgnoreCase(key) ) {
				String sufijo = request.getParameter(key);
				if( sufijo != null  &&  sufijo.trim().length() > 0 ) {
					nombreJS = nombreJS + "_" + sufijo.trim();
				}
			}
			else {
				al.add(key.trim().toLowerCase());
				String value = request.getParameter(key);
				if( value != null ) {
					value = value.trim();
					if( value.length() > 0 ) {
						al.add(value.toLowerCase());
					}
				}
			}
		}
		return generadorJsProperties.build(request.getServletContext().getClassLoader(), nombreControlador, nombreJS, locale, al.toArray(new String[al.size()]));
	}

	@Override
	public void destroy() {}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}
}
