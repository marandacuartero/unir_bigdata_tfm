package org.serest4j.async;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpServletResponse;

public class DirectOutputDataConsumer implements BufferDataConsumer {

	private final HttpServletResponse response;
	private String contentType = null;
	private String contentName = null;
	private int size = -1;
	private int nMaxBuffer = 8 * 1024;
	private ByteArrayOutputStream bout = new ByteArrayOutputStream(nMaxBuffer);

	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicBoolean started = new AtomicBoolean(false);

	public DirectOutputDataConsumer(HttpServletResponse response) {
		this.response = response;
	}

	public void setContent(String contentType, String contentName) {
		this.contentType = contentType;
		this.contentName = contentName;
	}

	public void setNMaxBuffer(int nMaxBuffer) {
		this.nMaxBuffer = nMaxBuffer;
	}

	public void setSize(int size) {
		this.size = size;
	}

	private void _start() {
		if( started.compareAndSet(false, true) ) {
			bout.reset();
			if( size > 0 ) {
				response.setContentLength(size);
			}
			if( contentType != null )
				response.setContentType(contentType);
			if( contentName != null ) {
				StringBuilder sb = new StringBuilder();
				sb.append("attachment;filename=").append('"');
				sb.append(contentName);
				sb.append('"');
				response.setHeader("Content-Disposition", sb.toString());
			}
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (closed) {
			_start();
			if( closed.compareAndSet(false, true) ) {
				response.getOutputStream().write(bout.toByteArray());
				response.getOutputStream().flush();
			}
		}
	}


	@Override
	public void consume(Object obj) throws IOException {
		synchronized (closed) {
			if( closed.get() )
				throw new EOFException();
			_start();
			if( obj == null ) {}
			else if( obj instanceof CharSequence  ||  obj instanceof Number  ||  obj instanceof Character  ||  obj instanceof Boolean ) {
				bout.write( String.valueOf(obj).getBytes() );
			}
			else {
				try {
					byte[] b = (byte[])obj;
					bout.write(b);
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
			if( bout.size() > nMaxBuffer ) {
				byte[] b = bout.toByteArray();
				bout.reset();
				response.getOutputStream().write(b);
			}
		}
	}

	@Override
	public void flush() throws IOException {
		synchronized (closed) {
			if( closed.get() )
				throw new EOFException();
			_start();
			if( bout.size() > 0 ) {
				byte[] b = bout.toByteArray();
				bout.reset();
				response.getOutputStream().write(b);
				response.getOutputStream().flush();
			}
		}
	}
}
