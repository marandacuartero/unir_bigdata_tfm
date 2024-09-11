package org.serest4j.http.idserver.policy;

import java.util.Arrays;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class ClassListUserCredentials implements CredencialsInterface {

	private String[] permisos;

	private ClassListUserCredentials(){}

	public ClassListUserCredentials(String[] permisos) {
		this();
		buildPermisos(permisos);
	}

	@Override
	public boolean isValid(CredentialsType tipo) {
		return tipo == CredentialsType.CONTROLADOR;
	}

	@Override
	public boolean comprobarCredenciales(Logger debug, Object nombreMetodo) {
		boolean tienePermiso = false;
		if( nombreMetodo != null ) {
			String strNombreMetodo = nombreMetodo.toString().toLowerCase();
			if( permisos != null  &&  permisos.length > 0 ) {
				tienePermiso = existeControl(permisos, strNombreMetodo);
			}
			if( debug != null  &&  debug.isTraceEnabled() ) {
				debug.trace(nombreMetodo + ": permiso=" + tienePermiso + " con " + this.toString());
			}
		}
		return tienePermiso;
	}

	private void buildPermisos(String[] str) {
		String[] str2 = clean(str);
		this.permisos = str2;
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
					while( l > 0  &&  sb.charAt(0) == '.' ) {
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

	private boolean existeControl(String[] strings, String metodo) {
		for( String str : strings ) {
			if( metodo.startsWith(str) ) {
				return true;
			}
		}
		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(CredentialsType.CONTROLADOR + " [");
		sb.append("Permisos:").append(Arrays.toString(permisos));
		return sb.toString();
	}
}
