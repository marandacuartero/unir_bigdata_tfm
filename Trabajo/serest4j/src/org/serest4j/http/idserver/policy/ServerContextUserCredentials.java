package org.serest4j.http.idserver.policy;

import java.util.Arrays;

import org.apache.log4j.Logger;

import jakarta.servlet.ServletContext;


/**
 * Comprueba dominios
 * 
 * Por ejemplo '/admin/' admite todos los archivos que cuelgan del dominio '/admin/'
 * Por ejemplo '/admin/login.jsp' admite el archivo '/admin/login.jsp'
 * 
 * @author maranda
 *
 */
@SuppressWarnings("serial")
public class ServerContextUserCredentials implements CredencialsInterface {

	private String[] permisos;
	private String[] restricciones;
	private String pathBase;
	
	private ServerContextUserCredentials(){}

	public ServerContextUserCredentials(ServletContext contextoBase, String[] permisos, String[] restricciones) {
		this();
		this.pathBase = contextoBase.getContextPath().toLowerCase();
		buildPermisos(permisos);
		buildRestricciones(restricciones);
	}

	private void buildPermisos(String[] str) {
		String[] str2 = clean(str);
		this.permisos = str2;
	}

	private void buildRestricciones(String[] str) {
		String[] str2 = clean(str);
		this.restricciones = str2;
	}

	public ServerContextUserCredentials clonar(String... rest) {
		ServerContextUserCredentials dominioCredencialesUsuario = new ServerContextUserCredentials();
		dominioCredencialesUsuario.pathBase = pathBase;
		dominioCredencialesUsuario.permisos = permisos == null ? null : Arrays.copyOf(permisos, permisos.length);
		dominioCredencialesUsuario.restricciones = null;
		if( rest != null  &&  rest.length > 0 ) {
			if( restricciones != null  &&  restricciones.length > 0 ) {
				dominioCredencialesUsuario.restricciones = Arrays.copyOf(restricciones, restricciones.length + rest.length);
				System.arraycopy(rest, 0, dominioCredencialesUsuario.restricciones, restricciones.length, rest.length);
			}
			else {
				dominioCredencialesUsuario.restricciones = Arrays.copyOf(rest, rest.length);
			}
		}
		else if( restricciones != null  &&  restricciones.length > 0 ) {
			dominioCredencialesUsuario.restricciones = Arrays.copyOf(restricciones, restricciones.length);
		}
		dominioCredencialesUsuario.buildRestricciones(dominioCredencialesUsuario.restricciones);
		return dominioCredencialesUsuario;
	}

	private String[] clean(String[] str) {
		if( str != null  &&  str.length > 0 ) {
			int n = str.length;
			int j = 0;
			StringBuilder sb = new StringBuilder();
			for( int i=0; i<n; i++ ) {
				if( str[i] != null ) {
					sb.setLength(0);
					sb.append(str[i].trim().toLowerCase());
					int l = sb.length();
					while( l > 0  &&  sb.charAt(0) == '/' ) {
						sb.deleteCharAt(0);
						l = sb.length();
					}
					if( l > 0 ) {
						str[j] = sb.toString();
						j++;
					}
				}
			}
			if( j > 0 ) {
				return Arrays.copyOf(str, j);
			}
		}
		return null;
	}

	@Override
	public boolean isValid(CredentialsType tipo) {
		return tipo == CredentialsType.DOMINIO;
	}

	
	@Override
	public boolean comprobarCredenciales(Logger debug, Object dominio) {
		boolean tienePermiso = false;
		String strDominio = dominio.toString().toLowerCase();
		if( strDominio.startsWith(pathBase) ) {
			strDominio = strDominio.substring(pathBase.length());
			while( strDominio.length() > 0  &&  strDominio.charAt(0) == '/' ) {
				strDominio = strDominio.substring(1);
			}
			tienePermiso = true;
			if( permisos != null  &&  permisos.length > 0 ) {
				tienePermiso = existeControl(permisos, strDominio);
			}
			if( tienePermiso ) {
				if( restricciones != null  &&  restricciones.length > 0  &&  existeControl(restricciones, strDominio) ) {
					tienePermiso = false;
				}
			}
			if( debug != null ) {
				debug.debug(dominio + ": permiso=" + tienePermiso + " con " + this.toString());
			}
		}
		return tienePermiso;
	}

	public static boolean existeControl(String[] strings, String dominio) {
		for( String str : strings ) {
			if( str.charAt(0) == '*' ) {
				if( str.length() == 1 ) {
					return true;
				}
				else if( str.length() > 1  &&  dominio.endsWith(str.substring(1)) ) {
					return true;	
				}
			}
			else if( dominio.startsWith(str) ) {
				return true;
			}
		}
		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(CredentialsType.DOMINIO + " " + pathBase + " [");
		sb.append("Permisos:").append(Arrays.toString(permisos));
		sb.append(", Restricciones:").append(Arrays.toString(restricciones)).append(']');
		return sb.toString();
	}
}
