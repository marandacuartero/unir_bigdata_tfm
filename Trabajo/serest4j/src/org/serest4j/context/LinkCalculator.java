package org.serest4j.context;

import java.lang.reflect.Method;

import org.serest4j.annotation.endpoint.TMLinkController;
import org.serest4j.annotation.endpoint.TMLinkMethod;
import org.serest4j.annotation.policy.TMAccessRolesDefaultLink;

public class LinkCalculator {

	/**
	 * Para buscar el servicio en caso de redireccion entre varios
	 * @param method
	 * @throws RedirectLinkException
	 */
	static String nombreServicioEnlace(Method method) throws RedirectLinkException {
		String nombreServicioOriginal = method.getDeclaringClass().getName() + "." + method.getName();
		String aliasMetodoServicio = null;
		String claseDeEnlace = null;
		if( method.isAnnotationPresent(TMLinkMethod.class) ) {
			TMLinkMethod enlace = method.getAnnotation(TMLinkMethod.class);
			if( enlace.value() != null  &&  enlace.value().trim().length() > 0 ) {
				aliasMetodoServicio = enlace.value().trim();
			}
		}
		if( method.isAnnotationPresent(TMLinkController.class) ) {
			TMLinkController enlace = method.getAnnotation(TMLinkController.class);
			if( enlace.controller() != null  &&  !enlace.controller().equals(Object.class) ) {
				claseDeEnlace = enlace.controller().getName();
			}
			else if( enlace.value() != null  &&  enlace.value().trim().length() > 0 ) {
				claseDeEnlace = enlace.value().trim();
			}
		}
		else if( method.getDeclaringClass().isAnnotationPresent(TMLinkController.class) ) {
			TMLinkController enlace = method.getDeclaringClass().getAnnotation(TMLinkController.class);
			if( enlace.controller() != null  &&  !enlace.controller().equals(Object.class) ) {
				claseDeEnlace = enlace.controller().getName();
			}
			else if( enlace.value() != null  &&  enlace.value().trim().length() > 0 ) {
				claseDeEnlace = enlace.value().trim();
			}
		}
		if( claseDeEnlace != null  ||  aliasMetodoServicio != null ) {
			if( claseDeEnlace == null )
				claseDeEnlace = method.getDeclaringClass().getName();
			if( aliasMetodoServicio == null )
				aliasMetodoServicio = method.getName();
			String nombreServicio = _nombreServicioControlador(claseDeEnlace, method, aliasMetodoServicio);
			if( nombreServicio != null  &&  !nombreServicio.trim().isEmpty() ) {
				if( nombreServicioOriginal.equals(nombreServicio.trim()) ) {
					// el enlace apunta hacia el mismo servicio original
				}
				else {
					throw new RedirectLinkException(nombreServicio.trim());
				}
			}
		}
		return nombreServicioOriginal;
	}

	/**
	 * Para buscar el servicio correcto y publicarlo en un proxy
	 * @param method
	 * @return
	 */
	public static String nombreServicioControlador(Method method) {
		try {
			return nombreServicioEnlace(method);
		} catch (RedirectLinkException e) {
			return e.getNuevoNombreServicio();
		}
	}

	private static String _nombreServicioControlador(String claseDeEnlace, Method method, String aliasMetodoServicio) {
		return __nombreServicioControlador(claseDeEnlace, method.getDeclaringClass().getName(), aliasMetodoServicio);
	}

	private static String __nombreServicioControlador(String claseDeEnlace, String methodClass, String aliasMetodoServicio) {
		String nombreServicio = claseDeEnlace + "." + aliasMetodoServicio;
		if( claseDeEnlace.length() > 0  &&  !claseDeEnlace.equals("=") ) {
			if( claseDeEnlace.startsWith("+") ) {
				claseDeEnlace = claseDeEnlace.substring(1).trim();
				if( claseDeEnlace.length() > 0 ) {
					claseDeEnlace = methodClass + claseDeEnlace;
					nombreServicio = claseDeEnlace + "." + aliasMetodoServicio;
				}
			}
			else if( claseDeEnlace.endsWith("+") ) {
				claseDeEnlace = claseDeEnlace.substring(0, claseDeEnlace.length() - 1).trim();
				if( claseDeEnlace.length() > 0 ) {
					int liop = methodClass.lastIndexOf('.') + 1;
					claseDeEnlace = methodClass.substring(0, liop) + claseDeEnlace + methodClass.substring(liop);
					nombreServicio = claseDeEnlace + "." + aliasMetodoServicio;
				}
			}
			else {
				int io = claseDeEnlace.indexOf(">>");
				if( io >= 0 ) {
					String str1 = claseDeEnlace.substring(0, io).trim();
					String str2 = claseDeEnlace.substring(io + 2).trim();
					claseDeEnlace = methodClass;
					if( str1.length() > 0 ) {
						io = claseDeEnlace.indexOf(str1);
						if( io >= 0 ) {
							claseDeEnlace = claseDeEnlace.substring(0, io) + str2 + claseDeEnlace.substring(io + str1.length());
							nombreServicio = claseDeEnlace + "." + aliasMetodoServicio;
						}
					}
					else {
						io = claseDeEnlace.lastIndexOf('.');
						if( io >= 0 ) {
							claseDeEnlace = claseDeEnlace.substring(0, io + 1) + str2;
							nombreServicio = claseDeEnlace + "." + aliasMetodoServicio;
						}
					}
				}
			}
		}
		return nombreServicio;
	}

	static void nombreServicioEnlaceMetodo(String mapeoServicio, Method method) throws RedirectLinkException {
		TMAccessRolesDefaultLink enlace = method.getAnnotation(TMAccessRolesDefaultLink.class);
		if( enlace != null ) {
			String claseDeEnlace = null;
			String aliasMetodoServicio = null;
			if( method.isAnnotationPresent(TMLinkMethod.class) ) {
				TMLinkMethod enlace2 = method.getAnnotation(TMLinkMethod.class);
				if( enlace2.value() != null  &&  enlace2.value().trim().length() > 0 ) {
					aliasMetodoServicio = enlace2.value().trim();
				}
			}
			if( enlace.controller() != null  &&  !enlace.controller().equals(Object.class) ) {
				claseDeEnlace = enlace.controller().getName();
			}
			else if( enlace.value() != null  &&  enlace.value().trim().length() > 0 ) {
				claseDeEnlace = enlace.value().trim();
			}
			if( claseDeEnlace != null ) {
				String nombreServicio = _nombreServicioControlador(claseDeEnlace, method, aliasMetodoServicio);
				if( mapeoServicio.equalsIgnoreCase(nombreServicio.trim()) ) {}
				else if( nombreServicio != null  &&  !nombreServicio.trim().isEmpty() ) {
					throw new RedirectLinkException(nombreServicio.trim());
				}
			}
		}
	}

	static void nombreServicioEnlaceControlador(String mapeoServicio, Class<?> controlador) throws RedirectLinkException {
		TMAccessRolesDefaultLink enlace = controlador.getAnnotation(TMAccessRolesDefaultLink.class);
		if( enlace != null ) {
			int liop = mapeoServicio.lastIndexOf('.');
			String claseDeEnlace = null;
			if( enlace.controller() != null  &&  !enlace.controller().equals(Object.class) ) {
				claseDeEnlace = enlace.controller().getName();
			}
			else if( enlace.value() != null  &&  enlace.value().trim().length() > 0 ) {
				claseDeEnlace = enlace.value().trim();
			}
			if( claseDeEnlace != null ) {
				String nombreServicio = __nombreServicioControlador(claseDeEnlace, mapeoServicio.substring(0, liop), mapeoServicio.substring(liop + 1));
				if( mapeoServicio.equalsIgnoreCase(nombreServicio.trim()) ) {}
				else if( nombreServicio != null  &&  !nombreServicio.trim().isEmpty() ) {
					throw new RedirectLinkException(nombreServicio.trim());
				}
			}
		}
	}
}
