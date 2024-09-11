package org.serest4j.http.idserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.serest4j.common.FileLogger;
import org.serest4j.http.ExtendTimeInResponse;
import org.serest4j.http.HttpResponseErrorCode;
import org.serest4j.http.UnidentifiedUserException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TMIdSesionFilter implements Filter {

	private String redireccionPermisos;
	private String redireccionLogin;
	private String[] include;
	private String[] exclude;
	private int segundosDeEspera;
	private String contextPath;

	@Override
	public void destroy() {}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		boolean close = true;
		if( servletRequest instanceof HttpServletRequest ) {
			HttpServletRequest request = (HttpServletRequest)servletRequest;
			boolean validarServicio = include == null  ||  validarCadena(request.getServletPath(), include);
			validarServicio = validarServicio  &&  !validarCadena(request.getServletPath(), exclude);  
			if( validarServicio ) {
				try {
					if( HttpKeyValidator.validateSession(request) ) {
						filterChain.doFilter(servletRequest, servletResponse);
						close = false;
					}
					else if( redireccionPermisos != null  &&  redireccionPermisos.equals(request.getServletPath()) ) {
						filterChain.doFilter(servletRequest, servletResponse);
						close = false;
					}
					else if( redireccionPermisos != null ) {
						((HttpServletResponse)servletResponse).sendRedirect(redireccionPermisos);
						close = false;
					}
					else {
						((HttpServletResponse)servletResponse).sendError(HttpResponseErrorCode.CREDENCIALES_INSUFICIENTES);
					}
				} catch(UnidentifiedUserException se) {
					FileLogger.getLogger(contextPath).error("doFilter " + se);
					if( redireccionLogin != null ) {
						((HttpServletResponse)servletResponse).sendRedirect(redireccionLogin);
						close = false;
					}
					else {
						ExtendTimeInResponse.procesar(segundosDeEspera);
						((HttpServletResponse)servletResponse).sendError(HttpResponseErrorCode.SESION_NO_VALIDA);
					}
				}
			}
			else {
				HttpKeyValidator.isValidSession(request);
				filterChain.doFilter(servletRequest, servletResponse);
				close = false;
			}
		}
		if( close ) {
			servletResponse.getOutputStream().flush();
			servletResponse.getOutputStream().close();
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}

	public void initIdFilter(String _contexto, String _redireccionPermisos, String _redireccionLogin, String _include, String _exclude, int _secsEspera, Logger logger) {// throws ServletException {
		//PropertiesLoader loadGssProperties = ServerStaticContext.get(contextPath).getPropertiesLoader();
		this.contextPath = _contexto;
		this.segundosDeEspera = _secsEspera; // loadGssProperties.getInteger("serest4j.unautorized.delay", 0);
		logger.debug("Lanzado filtro de control de sesion >> " + contextPath);
		//String _redireccionPermisos = filterConfig.getInitParameter("redireccionPermisos");
		if( _redireccionPermisos != null  &&  _redireccionPermisos.trim().length() > 0 ) {
			redireccionPermisos = _redireccionPermisos.trim();
		}
		else {
			redireccionPermisos = null;
		}
		//String _redireccionLogin = filterConfig.getInitParameter("redireccionLogin");
		if( _redireccionLogin != null  &&  _redireccionLogin.trim().length() > 0 ) {
			redireccionLogin = _redireccionLogin.trim();
		}
		else {
			redireccionLogin = null;
		}
		ArrayList<String> al = new ArrayList<String>();
		if( redireccionLogin != null )
			al.add(redireccionLogin);
		//String _exclude = filterConfig.getInitParameter("exclude");
		if( _exclude != null ) {
			exclude = _exclude.split(";");
			for( String str : exclude ) {
				if( str != null  &&  str.trim().length() > 0 ) {
					al.add(str.trim());
				}
			}
		}
		if( al.size() <= 0 )
			exclude = null;
		else {
			exclude = new String[al.size()];
			exclude = al.toArray(exclude);
		}
		al.clear();
		//String _include = filterConfig.getInitParameter("include");
		if( _include != null ) {
			include = _include.split(";");
			for( String str : include ) {
				if( str != null  &&  str.trim().length() > 0 ) {
					al.add(str.trim());
				}
			}
		}
		if( al.size() <= 0 )
			include = null;
		else {
			include = new String[al.size()];
			include = al.toArray(include);
		}
		al.clear();
		al = null;
		if( include != null ) {
			logger.debug("Lanzado filtro de control de sesion, paths incluidos >> " + Arrays.toString(include));
		}
		if( exclude != null ) {
			logger.debug("Lanzado filtro de control de sesion, paths excluidos >> " + Arrays.toString(exclude));
		}
		if( redireccionPermisos != null ) {
			redireccionPermisos = contextPath + redireccionPermisos;
			logger.debug("Redireccion del filtro de id sesion sin credenciales >> " + redireccionPermisos);
		}
		if( redireccionLogin != null ) {
			redireccionLogin = contextPath + redireccionLogin;
			logger.debug("Redireccion del filtro de id sesion sin id de sesion >> " + redireccionLogin);
		}
		if( redireccionPermisos == null  &&  redireccionLogin != null ) {
			redireccionPermisos = redireccionLogin;
			logger.debug("Redireccion del filtro de id sesion sin credenciales >> " + redireccionPermisos);
		}
		if( redireccionPermisos != null  &&  redireccionLogin == null ) {
			redireccionLogin = redireccionPermisos;
			logger.debug("Redireccion del filtro de id sesion sin id de sesion >> " + redireccionLogin);
		}
	}

	private boolean validarCadena(String path, String[] cadenas) {
		if( cadenas != null  &&  cadenas.length > 0 ) {
			for( String str : cadenas ) {
				if( str.charAt(0) == '*' ) {
					if( str.length() == 1 ) {
						return true;
					}
					else if( str.length() > 1  &&  path.endsWith(str.substring(1)) ) {
						return true;
					}
				}
				else if( str.endsWith("*") ) {
					if( str.length() > 1  &&  path.startsWith(str.substring(0, str.length() - 1)) ) {
						return true;
					}
				}
				else if(path.equalsIgnoreCase(str)) {
					return true;
				}
			}
		}
		return false;
	}
}
