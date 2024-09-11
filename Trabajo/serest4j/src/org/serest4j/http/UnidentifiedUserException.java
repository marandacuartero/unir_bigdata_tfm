package org.serest4j.http;

@SuppressWarnings("serial")
public class UnidentifiedUserException extends Exception {

	public UnidentifiedUserException(String msg) {
		super(msg);
	}
}
