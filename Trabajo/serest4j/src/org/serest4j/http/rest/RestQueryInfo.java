package org.serest4j.http.rest;

import java.util.ArrayList;
import java.util.Collection;

public class RestQueryInfo {

	private String forward, tipoForward;
	private String contentType;
	private String contentName;
	private String servicioSistema;

	private final ArrayList<String> argumentos;

	public RestQueryInfo() {
		this.argumentos = new ArrayList<String>();
	}

	public String getForward() {
		return forward;
	}

	public String getTipoForward() {
		return tipoForward;
	}

	public void setForward(String forward, String tipo) {
		this.forward = forward;
		this.tipoForward = forward == null ? null : tipo;
	}

	public String getContentType() {
		return contentType;
	}

	public String getContentName() {
		return contentName;
	}

	public void setContent(String contentType, String contentName) {
		this.contentType = contentType;
		this.contentName = contentName;
	}

	public String getServicioSistema() {
		return servicioSistema;
	}

	public void setServicioSistema(String servicioSistema) {
		this.servicioSistema = servicioSistema;
	}

	public synchronized Object[] getArgumentos() {
		return argumentos.toArray();
	}

	public synchronized int size() {
		return argumentos.size();
	}

	public synchronized void setArgumentos(Collection<String> c) {
		argumentos.clear();
		if( c != null ) {
			argumentos.addAll(c);
		}
	}

	@Override
	public String toString() {
		return "ConversorQueryHeader [forward=" + forward
				+ ", contentType=" + contentType
				+ ", servicioSistema=" + servicioSistema
				+ ", argumentos=" + argumentos + "]";
	}
}
