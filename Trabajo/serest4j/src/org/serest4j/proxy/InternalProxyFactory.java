package org.serest4j.proxy;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.common.FileLogger;
import org.serest4j.context.ControllerFactory;
import org.serest4j.context.LinkCalculator;
import org.serest4j.context.TMContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Acceso a controladores desde dentro de un jsp o un servlet del mismo servidor, sin comprobar la propia Http request del usuario
 * ya que se utiliza solamente en puntos donde estas comprobaciones ya se han llevado a cabo previamente.
 * 
 * @author maranda
 *
 */
public class InternalProxyFactory implements InvocationHandler {

	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> controlador, TMContext contexto) {
		InternalProxyFactory internalProxyFactory = new InternalProxyFactory(contexto.getRequest(), null);
		ClassLoader classLoader = contexto.getRequest().getServletContext().getClassLoader();
		Object newProxyInstance = Proxy.newProxyInstance(classLoader, new Class[] { controlador }, internalProxyFactory);
		return (T) newProxyInstance;	
	}

	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> controlador, HttpServletRequest request) {
		InternalProxyFactory internalProxyFactory = new InternalProxyFactory(request, null);
		ClassLoader classLoader = request.getServletContext().getClassLoader();
		Object newProxyInstance = Proxy.newProxyInstance(classLoader, new Class[] { controlador }, internalProxyFactory);
		return (T) newProxyInstance;	
	}

	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> controlador, String contexto) {
		InternalProxyFactory internalProxyFactory = new InternalProxyFactory(null, contexto.trim());
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Object newProxyInstance = Proxy.newProxyInstance(classLoader, new Class[] { controlador }, internalProxyFactory);
		return (T) newProxyInstance;	
	}

	private Logger debug;
	private Logger error;
	private String contexto;
	private HttpServletRequest request;

	private InternalProxyFactory(HttpServletRequest request, String contexto) {
		if( request != null ) {
			this.contexto = request.getContextPath();
			this.request = request;
		}
		else {
			this.contexto = contexto.trim();
			this.request = null;
		}
		this.error = FileLogger.getLogger(this.contexto);
		this.debug = this.error.isDebugEnabled() ? this.error : null;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] argumentos) throws Throwable {
		String nombreServicio = LinkCalculator.nombreServicioControlador(method);
		try {
			ControllerFactory cf = ControllerFactory.get(contexto);
			Object retorno = cf.procesarInvocacion(request, nombreServicio, false, true, argumentos);
			if( retorno instanceof BufferDataProvider ) {
				retorno = procesarBufferDataProvider((BufferDataProvider)retorno, debug);	
			}
			return retorno;
		}
		catch(Throwable e) {
			error.error("Invocando " + nombreServicio, e);
			Throwable th = e;
			while( th.getCause() != null ) {
				th = th.getCause();
			}
			throw th;
		}
	}

	private QueuedBufferDataConsumer procesarBufferDataProvider(BufferDataProvider bufferDataProvider, Logger debug) throws IOException {
		QueuedBufferDataConsumer consumer = new QueuedBufferDataConsumer();
		bufferDataProvider.setConsumer(consumer);
		Thread th = new Thread(bufferDataProvider.getRunnableContext());
		th.start();
		if( consumer.hasNext() ) {
			if( debug != null ) {
				int n = consumer.getSize();
				debug.debug("Generando datos al vuelo " + (n != -1 ? "con " + n + " datos" : ""));
			}
		}
		else {
			if( debug != null ) {
				debug.debug("Generando datos al vuelo no obtuvo ningun dato");
			}
		}
		return consumer;
	}
}
