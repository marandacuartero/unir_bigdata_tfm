package org.serest4j.buffers.cloud;

import java.io.Serializable;

@SuppressWarnings("serial")
public class GenericContainer implements Serializable, Comparable<GenericContainer> {

	private String key;
	private String value;

	public GenericContainer(String key, String value) {
		setKey(key);
		setValue(value);
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "key=" + key;
	}

	@Override
	public int compareTo(GenericContainer o) {
		return key.compareTo(o.key);
	}
}
