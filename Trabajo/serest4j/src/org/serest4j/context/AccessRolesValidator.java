package org.serest4j.context;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.serest4j.annotation.policy.TMAccessRoles;
import org.serest4j.annotation.service.TMLoginService;
import org.serest4j.annotation.service.TMLogoutService;
import org.serest4j.http.idserver.policy.CredencialsInterface;
import org.serest4j.http.idserver.policy.CredentialsType;

public class AccessRolesValidator {

	public static boolean comprobarLogin(Method method, Logger debug) {
		TMLoginService isLoginService = method.getAnnotation(TMLoginService.class);
		if( isLoginService != null ) {
			if( debug != null ) {
				debug.trace("Solicitud de login para " + method);
			}
			return true;
		}
		return false;
	}

	public static boolean comprobarLogout(Method method, Logger debug) {
		TMLogoutService isLogoutService = method.getAnnotation(TMLogoutService.class);
		if( isLogoutService != null ) {
			if( debug != null ) {
				debug.trace("Solicitud de logout para " + method);
			}
			return true;
		}
		return false;
	}

	public static boolean comprobarTodosRoles(CredencialsInterface[] credencialesUsuarios, String[] deClase, String[] deMetodo, Logger debug) {
		return comprobarValuesRoles(deClase, credencialesUsuarios, debug)  &&  comprobarValuesRoles(deMetodo, credencialesUsuarios, debug);
	}

	public static boolean comprobarControlador(Class<?> clase, CredencialsInterface[] credencialesUsuarios, Logger debug) {
		TMAccessRoles rolesAsociados = clase.getAnnotation(TMAccessRoles.class);
		return comprobarRolesAsociados(rolesAsociados, credencialesUsuarios, debug);
	}

	public static boolean comprobarMetodo(Method method, CredencialsInterface[] credencialesUsuarios, Logger debug) {
		TMAccessRoles rolesAsociados = method.getAnnotation(TMAccessRoles.class);
		if( comprobarRolesAsociados(rolesAsociados, credencialesUsuarios, debug) ) {
			if( method.getDeclaringClass().isInterface() ) {
				return comprobarControlador(method.getDeclaringClass(), credencialesUsuarios, debug);
			}
			else {
				return true;
			}
		}
		return false;
	}

	private static boolean comprobarRolesAsociados(TMAccessRoles rolesAsociados, CredencialsInterface[] credencialesUsuarios, Logger debug) {
		if( rolesAsociados != null ) {
			String[] values = rolesAsociados.value();
			return comprobarValuesRoles(values, credencialesUsuarios, debug);
		}
		return true;
	}

	private static boolean comprobarValuesRoles(String[] values, CredencialsInterface[] credencialesUsuarios, Logger debug) {
		if( values != null  &&  values.length > 0 ) {
			if( credencialesUsuarios != null  &&  credencialesUsuarios.length > 0 ) {
				for( String rol : values ) {
					for( CredencialsInterface cu : credencialesUsuarios ) {
						if( cu != null  &&  cu.isValid(CredentialsType.ROL) ) {
							if( cu.comprobarCredenciales(debug, rol) ) {
								return true;
							}
						}
					}
				}
			}
			if( debug != null ) {
				debug.trace("Credenciales incorrectas para " + Arrays.toString(values));
			}
			return false;
		}
		return true;
	}
}
