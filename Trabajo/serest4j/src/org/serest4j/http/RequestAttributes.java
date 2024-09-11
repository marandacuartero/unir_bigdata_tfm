package org.serest4j.http;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.serest4j.common.FileLogger;
import org.serest4j.common.Version;
import org.serest4j.context.ServerStaticContext;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestAttributes {

	private RequestAttributes() {}

	public static final String CONTENT_TYPE = "CONTENTTYPE" + Version.VALUE;
	public static final String CONTENT_NAME = "CONTENTNAME" + Version.VALUE;
	public static final String FORWARD_NAME = "FWD" + Version.VALUE;
	public static final String FORWARD_TYPE = "FWDTYPE" + Version.VALUE;

	public static final String USER_CREDENTIALS = "fwjspUserCredentials";
	public static final String RESPUESTA_SERVICIO = "fwjspRespuesta";
	public static final String ID_SESION = "fwjspIdSesion";
	public static final String USER_CODE = "fwjspUserCode";
	public static final String USER_SESION = "fwjspUserSesion";
	private static final String BREAD_CRUMS = "fwjspBreadCrumbsServletContext";

	public static final String COOKIE_ID = "SALSSID" + Version.VALUE;

	public static final String[] getBreadCrumbsServletContext(HttpServletRequest request) {
		if( request != null ) {
			Object obj = request.getAttribute(BREAD_CRUMS);
			if( obj == null ) {
				return new String[]{request.getServletPath()};
			}
			else {
				return (String[])obj;
			}
		}
		return null;
	}

	public static final void addBreadCrumbsServletContext(HttpServletRequest request) {
		if( request != null ) {
			Object obj = request.getAttribute(BREAD_CRUMS);
			if( obj == null ) {
				request.setAttribute(BREAD_CRUMS, new String[]{request.getServletPath()});
			}
			else {
				String[] arrstr = (String[])obj;
				if( arrstr[arrstr.length - 1] == null ) {
					arrstr[arrstr.length - 1] = request.getServletPath();
				}
				else if( !arrstr[arrstr.length - 1].equals(request.getServletPath()) ) {
					arrstr = Arrays.copyOf(arrstr, arrstr.length + 1);
					arrstr[arrstr.length - 1] = request.getServletPath();
				}
				request.setAttribute(BREAD_CRUMS, arrstr);
			}
		}
	}

	public static final Object get(HttpServletRequest request) {
		return request.getAttribute(RequestAttributes.RESPUESTA_SERVICIO);
	}

	public static final String getSessionId(HttpServletRequest request) {
		Object obj = request.getAttribute(RequestAttributes.ID_SESION);
		return obj == null ? null : obj.toString();
	}

	public static final String getUserCode(HttpServletRequest request) {
		Object obj = request.getAttribute(RequestAttributes.USER_CODE);
		return obj == null ? null : obj.toString();
	}

	public static final Object getUserSession(HttpServletRequest request) {
		return request.getAttribute(RequestAttributes.USER_SESION);
	}

	public static final Logger getUserLogger(HttpServletRequest request) {
		Object userLoggerObject = request.getAttribute(USER_CODE);
		if( userLoggerObject != null  &&  userLoggerObject instanceof String ) {
			String userLoggerName = String.valueOf(userLoggerObject).trim();
			if( userLoggerName.length() > 0 ) {
				Logger userLogger = FileLogger.getLogger(request.getContextPath(), "users.user_" + userLoggerName.toLowerCase());
				return userLogger.isDebugEnabled() ? userLogger : null;
			}
		}
		return null;
	}
	
	public static String getProperty(HttpServletRequest request, String keyPropiedad) {
		return getProperty(request, keyPropiedad, null);
	}

	public static String getProperty(HttpServletRequest request, String keyPropiedad, String defaultValue) {
		return ServerStaticContext.get(request.getContextPath()).getPropertiesLoader().getProperty(keyPropiedad, defaultValue);
	}

	public static int getInteger(HttpServletRequest request, String keyPropiedad, int defaultValue) {
		return ServerStaticContext.get(request.getContextPath()).getPropertiesLoader().getInteger(keyPropiedad, defaultValue);
	}

	public static boolean getBoolean(HttpServletRequest request, String keyPropiedad) {
		return ServerStaticContext.get(request.getContextPath()).getPropertiesLoader().getBoolean(keyPropiedad);
	}
}
