package org.serest4j.http.server;

@SuppressWarnings("serial")
public class BadCredentialsException extends Exception {

	BadCredentialsException(String msg) {
		super(msg);
	}
}
