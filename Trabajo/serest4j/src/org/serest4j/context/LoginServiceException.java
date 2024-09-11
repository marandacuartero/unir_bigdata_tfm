package org.serest4j.context;

@SuppressWarnings("serial")
public class LoginServiceException extends Exception {

	private Object sesionUsuario = null;

	LoginServiceException(Object sesionUsuario) {
		this.sesionUsuario = sesionUsuario;
	}

	public Object getSesionUsuario() {
		return sesionUsuario;
	}
}
