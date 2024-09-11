package org.serest4j.async;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.serest4j.cripto.NoiseFactory;

public class SerializedDataConsumer implements BufferDataConsumer {

	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private final ObjectOutputStream out;
	private final byte[] clave;
	private final Object[] bufferDatosRespuesta = new Object[]{null};
	private AtomicInteger size = new AtomicInteger(-1);

	public SerializedDataConsumer(ObjectOutputStream out, byte[] clave) {
		this.out = out;
		this.clave = clave;
	}

	@Override
	public void setSize(int size) {
		this.size.set(size);
	}

	private void _start() throws IOException {
		if( initialized.compareAndSet(false, true) ) {
			bufferDatosRespuesta[0] = new BufferDataInit(size.get());
			String[] strDatos = NoiseFactory.encriptaChunked(null, clave, bufferDatosRespuesta, null);
			for( String utf : strDatos ) {
				out.writeUTF(utf);	
			}
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (closed) {
			if( closed.compareAndSet(false, true) ) {
				_start();
				bufferDatosRespuesta[0] = new BufferDataEof();
				String[] strDatos = NoiseFactory.encriptaChunked(null, clave, bufferDatosRespuesta, null);
				for( String utf : strDatos ) {
					out.writeUTF(utf);	
				}
			}
		}
		out.flush();
	}

	@Override
	public void consume(Object obj) throws IOException {
		synchronized (closed) {
			if( closed.get() )
				throw new EOFException();
			_start();
			bufferDatosRespuesta[0] = obj;
			String[] strDatos = NoiseFactory.encriptaChunked(null, clave, bufferDatosRespuesta, null);
			if( strDatos != null ) {
				for( String utf : strDatos ) {
					out.writeUTF(utf);	
				}
				out.flush();
			}
		}
	}

	@Override
	public void flush() throws IOException {}

	@Override
	public void setNMaxBuffer(int nMaxBuffer) {}
}
