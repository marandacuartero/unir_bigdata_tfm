package org.serest4j.context;

@SuppressWarnings("serial")
public class RedirectLinkException extends Exception {

	private final String nuevoNombreServicio;

	RedirectLinkException(String nuevoNombreServicio) {
		super(" >> " + nuevoNombreServicio);
		this.nuevoNombreServicio = nuevoNombreServicio;
	}
	public String getNuevoNombreServicio() {
		return nuevoNombreServicio;
	}
}
