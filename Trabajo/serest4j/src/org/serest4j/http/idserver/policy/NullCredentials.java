package org.serest4j.http.idserver.policy;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class NullCredentials implements CredencialsInterface {

	@Override
	public boolean isValid(CredentialsType metodo) { return false; }
	@Override
	public boolean comprobarCredenciales(Logger debug, Object metodo) { return false; }
}
