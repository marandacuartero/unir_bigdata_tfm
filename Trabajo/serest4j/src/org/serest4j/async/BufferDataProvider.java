package org.serest4j.async;

import java.io.IOException;
import java.util.Iterator;

/**
 * Objeto que permite la invocacion al vuelo de un servicio del servidor, de forma que el servidor actua como proveedor de datos
 * mientras que el cliente actua como consumidor de los mismos, y puede ir leyendo datos del servidor de manera simultanea a la
 * que el servidor los va construyendo.
 * 
 * @author Maranda
 *
 * @see Iterator
 *
 */
public class BufferDataProvider {

	private BufferDataConsumer bufferDataConsumer;
	private Runnable runnable;

	public void setConsumer(BufferDataConsumer bufferDataConsumer) {
		this.bufferDataConsumer = bufferDataConsumer;
	}

	public void setRunnableContext(Runnable runnable) {
		this.runnable = runnable;
	}

	public Runnable getRunnableContext() {
		return this.runnable;
	}

	public synchronized void setSize(int size) throws IOException {
		if( bufferDataConsumer == null ) {
			throw new IOException("BufferDataConsumer is closed");
		}
		else {
			bufferDataConsumer.setSize(size);
		}
	}

	public synchronized void setNMaxBuffer(int nMaxBuffer) throws IOException {
		if( bufferDataConsumer == null ) {
			throw new IOException("BufferDataConsumer is closed");
		}
		else {
			bufferDataConsumer.setNMaxBuffer(nMaxBuffer);
		}
	}

	public synchronized void setContent(String contentType, String contentName) throws IOException {
		if( bufferDataConsumer == null ) {
			throw new IOException("BufferDataConsumer is closed");
		}
		else if( bufferDataConsumer instanceof DirectOutputDataConsumer ) {
			((DirectOutputDataConsumer) bufferDataConsumer).setContent(contentType, contentName);
		}
	}

	public synchronized BufferDataProvider send(Object obj) throws IOException {
		if( bufferDataConsumer == null ) {
			throw new IOException("BufferDataConsumer is closed");
		}
		else {
			bufferDataConsumer.consume(obj);
		}
		return this;
	}

	public synchronized BufferDataProvider flush() throws IOException {
		if( bufferDataConsumer == null ) {
			throw new IOException("BufferDataConsumer is closed");
		}
		else {
			bufferDataConsumer.flush();
		}
		return this;
	}

	public synchronized void close() {
		if( bufferDataConsumer != null ) {
			try {
				bufferDataConsumer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			bufferDataConsumer = null;
		}
	}
}
