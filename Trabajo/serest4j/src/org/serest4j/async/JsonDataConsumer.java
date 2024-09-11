package org.serest4j.async;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.serest4j.common.GSonFormatter;

import jakarta.servlet.http.HttpServletResponse;

public class JsonDataConsumer implements BufferDataConsumer {

	private final HttpServletResponse response;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final GSonFormatter gSonFormat;
	private final AtomicInteger l = new AtomicInteger(0);
	private final StringBuffer sbout;
	private int nMaxBuffer = 8 * 1024;

	public JsonDataConsumer(HttpServletResponse response, GSonFormatter gSonFormat) {
		this.response = response;
		this.gSonFormat = gSonFormat;
		this.nMaxBuffer = 8 * 1024;
		this.sbout = new StringBuffer(8 * 1024);
	}

	private void _init() throws IOException {
		if( started.compareAndSet(false, true) ) {
			response.setContentType("text/x-json");
			response.setCharacterEncoding("UTF-8");
			response.getOutputStream().print("[");
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (closed) {
			_init();
			if( closed.compareAndSet(false, true) ) {
				sbout.setLength(l.get());
				if( sbout.length() > 0 ) {
					response.getOutputStream().print(sbout.toString());
				}
				response.getOutputStream().println("]");
				response.getOutputStream().flush();
			}
		}
	}

	@Override
	public void consume(Object obj) throws IOException {
		synchronized (closed) {
			_init();
			if( closed.get() )
				throw new EOFException();
			String strRespuesta = gSonFormat.toJson(obj);
			sbout.append(strRespuesta);
			if( sbout.length() >= nMaxBuffer ) {
				response.getOutputStream().print(sbout.toString());
				response.getOutputStream().flush();
				sbout.setLength(0);
			}
			l.set(sbout.length());
			sbout.append(',');
		}
	}

	@Override
	public void setSize(int size) {}

	@Override
	public void setNMaxBuffer(int nMaxBuffer) {
		this.nMaxBuffer = nMaxBuffer;
	}

	@Override
	public void flush() throws IOException {}
}
