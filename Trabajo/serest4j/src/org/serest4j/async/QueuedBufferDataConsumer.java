package org.serest4j.async;

import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class QueuedBufferDataConsumer implements BufferDataConsumer, Iterator<Object> {

	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicInteger nMaxBuffer = new AtomicInteger(8 * 1024);

	private ToroidQueue<Object> cola = new ToroidQueue<Object>();
	private AtomicInteger size = new AtomicInteger(-1);

	@Override
	public void close() throws IOException {
		synchronized (closed) {
			closed.set(true);
			closed.notifyAll();
		}
	}

	@Override
	public void consume(Object obj) throws IOException {
		boolean excepcion = false;
		boolean autoremove = false;
		synchronized (closed) {
			long l = 0;
			while( !closed.get()  &&  cola.size() > nMaxBuffer.get()  &&  !autoremove ) {
				try {
					closed.wait(1000l);
					if( l == 0 )
						l = System.currentTimeMillis() + 30000l;
					else if( System.currentTimeMillis() > l )
						autoremove = true;
				} catch (InterruptedException e) {}
			}
			if( closed.get() ) {
				excepcion = true;
			}
			else {
				cola.mete(obj);
			}
		}
		synchronized (closed) {
			closed.notifyAll();
		}
		if( autoremove ) {
			remove();
			excepcion = true;
		}
		if( excepcion )
			throw new EOFException();
	}

	@Override
	public boolean hasNext() {
		synchronized (closed) {
			while( !closed.get()  && cola.size() <= 0 ) {
				try { closed.wait(1000l); } catch (InterruptedException e) {}
			}
			return cola == null ? false : cola.size() > 0;
		}
	}

	@Override
	public Object next() {
		synchronized (closed) {
			Object retorno = cola.saca();
			closed.notifyAll();
			return retorno;
		}
	}

	@Override
	public void remove() {
		synchronized (closed) {
			closed.set(true);
			while( cola.saca() != null ) {}
			closed.notifyAll();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		closed.set(true);
		while( cola.saca() != null ) {}
	}

	@Override
	public void setSize(int size) {
		this.size.set(size);
	}

	public int getSize() {
		return size.get();	
	}

	@Override
	public void setNMaxBuffer(int nMaxBuffer) {
		this.nMaxBuffer.set(nMaxBuffer);
	}

	@Override
	public void flush() throws IOException {}
}
