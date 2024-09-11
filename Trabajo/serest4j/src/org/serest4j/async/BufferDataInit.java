package org.serest4j.async;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class BufferDataInit implements Serializable {

	private final int size;

	public BufferDataInit(int size) {
		this.size = size;
	}

	public int getSize() {
		return size;
	}
}
