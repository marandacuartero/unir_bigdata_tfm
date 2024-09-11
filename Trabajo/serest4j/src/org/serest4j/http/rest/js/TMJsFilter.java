package org.serest4j.http.rest.js;

import java.io.IOException;

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

public class TMJsFilter implements Filter {

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
				if( nombreControlador != null  &&  nombreControlador.length() > 0 ) {
					response.setContentType("text/javascript");
					response.setCharacterEncoding("UTF-8");
					byte[] b = nombreControlador.getBytes("UTF-8");
					response.getOutputStream().write(b);
					response.getOutputStream().flush();
					response.getOutputStream().close();
					return;
				}
			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	@Override
	public void destroy() {}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}
}
