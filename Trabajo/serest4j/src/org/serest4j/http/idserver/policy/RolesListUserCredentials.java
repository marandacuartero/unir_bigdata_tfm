package org.serest4j.http.idserver.policy;

import java.util.Arrays;
import java.util.TreeSet;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class RolesListUserCredentials implements CredencialsInterface {

	private String[] roles = null;
	private final String toStringValue;

	public RolesListUserCredentials(Object[] objRoles) {
		TreeSet<String> ts = new TreeSet<String>();
		if( objRoles != null  &&  objRoles.length > 0 ) {
			for( Object obj : objRoles ) {
				if( obj != null ) {
					String r = String.valueOf(obj);
					ts.add(r.trim().toUpperCase());
				}
			}
		}
		this.toStringValue = CredentialsType.ROL + " [enumRoles=" + ts + "]";
		this.roles = new String[ts.size()];
		this.roles = ts.toArray(this.roles);
		Arrays.sort(this.roles);
	}

	@Override
	public boolean isValid(CredentialsType tipo) {
		return tipo != null  &&  tipo == CredentialsType.ROL;
	}

	@Override
	public boolean comprobarCredenciales(Logger debug, Object rolId) {
		boolean tienePermiso = false;
		if( this.roles.length > 0  &&  rolId != null ) {
			String r = String.valueOf(rolId).trim().toUpperCase();
			if( Arrays.binarySearch(this.roles, r) >= 0 ) {
				tienePermiso = true;
			}
		}
		if( debug != null  &&  debug.isTraceEnabled() ) {
			debug.trace(rolId + ": permiso=" + tienePermiso + " con " + this.toStringValue);
		}
		return tienePermiso;
	}

	@Override
	public String toString() {
		return this.toStringValue;
	}
}
