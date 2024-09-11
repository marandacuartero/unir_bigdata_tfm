package org.serest4j.http.idserver.policy;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class TrueCredentials implements CredencialsInterface {
	
	@Override
	public boolean isValid(CredentialsType metodo) {
		return true;
	}
	
	@Override
	public boolean comprobarCredenciales(Logger debug, Object metodo) {
		return true;
	}
}
