package org.serest4j.context;


@SuppressWarnings("serial")
public class LogoutServiceException extends Exception {

	private Object logoutRetorno = null;

	LogoutServiceException(Object logoutRetorno) {
		this.logoutRetorno = logoutRetorno;
	}

	public Object getLogoutRetorno() {
		return logoutRetorno;
	}

	public void setLogoutRetorno(Object logoutRetorno) {
		this.logoutRetorno = logoutRetorno;
	}
}
