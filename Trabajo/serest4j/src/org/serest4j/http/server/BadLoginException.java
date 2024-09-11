package org.serest4j.http.server;


@SuppressWarnings("serial")
public class BadLoginException extends BadAccessException {

	BadLoginException() {
		super("Detectado intento de login reincidente fallido.");
	}
}
