package org.serest4j.http.server;

@SuppressWarnings("serial")
public class BadAccessException extends Exception {

	BadAccessException(String msg) {
		super(msg);
	}
}
