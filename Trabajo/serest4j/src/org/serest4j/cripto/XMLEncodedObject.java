package org.serest4j.cripto;

import java.io.Serializable;

@SuppressWarnings("serial")
public class XMLEncodedObject implements Serializable {

	private String object;

	public String getObject() {
		return object;
	}

	public void setObject(String object) {
		this.object = object;
	}

	@Override
	public String toString() {
		return object;
	}
}
