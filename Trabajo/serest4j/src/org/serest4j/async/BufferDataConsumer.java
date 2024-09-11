package org.serest4j.async;

import java.io.Closeable;
import java.io.IOException;

public interface BufferDataConsumer extends Closeable {

	public void setNMaxBuffer(int nMaxBuffer);

	public void setSize(int size);

	public void consume(Object obj) throws IOException;

	public void flush() throws IOException;
}
