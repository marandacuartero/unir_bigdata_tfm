package org.serest4j.proxy;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.common.FileLogger;
import org.serest4j.context.ControllerFactory;
import org.serest4j.context.LoginServiceException;
import org.serest4j.context.LogoutServiceException;
import org.serest4j.context.ServerStaticContext;
import org.serest4j.context.TMContext;
import org.serest4j.cripto.TokenFactory;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.UnidentifiedUserException;
import org.serest4j.http.idserver.HttpKeyValidator;
import org.serest4j.http.idserver.policy.UserDescriptorInstance;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Acceso a controladores desde dentro de un jsp o un servlet del mismo servidor, utilizando la propia Http request del usuario
 * Es obligatoria la existencia de un filtro del tipo FilterIdSesion que controle el id de sesion sobre la peticion
 * y que rellene estos atributos del request
 * 
 * @author maranda
 *
 */
public class LocalProxyFactory implements InvocationHandler {

	private static final String ID_ATRIBUTO_PROXY = TMContext.class.getName() + UUID.randomUUID().toString();

	public static <T> T getProxy(Class<T> controlador, HttpServletRequest request) {
		return getProxy(controlador, request, false);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> controlador, HttpServletRequest request, boolean throwsException) {
		Object proxy = request.getAttribute(ID_ATRIBUTO_PROXY);
		if( proxy == null ) {
			proxy = new LocalProxyFactory(request);
			request.setAttribute(ID_ATRIBUTO_PROXY, proxy);
		}
		LocalProxyFactory localProxyFactory = (LocalProxyFactory)proxy;
		localProxyFactory.setThrowsException(throwsException);
		ClassLoader classLoader = request.getServletContext().getClassLoader();
		Object newProxyInstance = Proxy.newProxyInstance(classLoader, new Class[] { controlador }, localProxyFactory);
		return (T) newProxyInstance;	
	}

	private HttpServletRequest request;
	private AtomicBoolean throwsException = new AtomicBoolean(false);
	private Logger logger;
	private UserDescriptorInstance userDescriptorInstance;
	private String contextpath;

	private LocalProxyFactory(HttpServletRequest request) {
		this.request = request;
		this.contextpath = request.getContextPath();
		this.logger = FileLogger.getLogger(this.contextpath.substring(1).toLowerCase());
		this.userDescriptorInstance = ServerStaticContext.get(contextpath).getUserDescriptorInstance();
	}

	public void setThrowsException(boolean throwsException) {
		this.throwsException.set(throwsException);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] argumentos) throws Throwable {
		String servicioSolicitado = method.getDeclaringClass().getName() + "." + method.getName();
		Logger userLogger = RequestAttributes.getUserLogger(request);
		if( userLogger == null  &&  logger != null  &&  logger.isDebugEnabled() )
			userLogger = logger;
		String idSesion = null;
		String codigoUsuario = null;
		Throwable th = null;
		try {
			if( userDescriptorInstance.comprobarCredencialesServicio(null, servicioSolicitado, userLogger) ) {
				Object retorno = ControllerFactory.get(contextpath).procesarInvocacion(request, servicioSolicitado, false, false, argumentos);
				if( retorno instanceof BufferDataProvider ) {
					retorno = procesarBufferDataProvider(codigoUsuario, (BufferDataProvider)retorno, userLogger);	
				}
				request.setAttribute(RequestAttributes.RESPUESTA_SERVICIO, retorno);
				return retorno;
			}
			else if( HttpKeyValidator.validateSession(request) ) {
				Object objetoSesionUsuario = request.getAttribute(RequestAttributes.USER_SESION);
				idSesion = (String)request.getAttribute(RequestAttributes.ID_SESION);
				codigoUsuario = (String)request.getAttribute(RequestAttributes.USER_CODE);
				if( userDescriptorInstance.comprobarCredencialesServicio(objetoSesionUsuario, servicioSolicitado, userLogger) ) {
					Object retorno = ControllerFactory.get(contextpath).procesarInvocacion(request, servicioSolicitado, false, false, argumentos);
					if( retorno instanceof BufferDataProvider ) {
						retorno = procesarBufferDataProvider(codigoUsuario, (BufferDataProvider)retorno, userLogger);
					}
					request.setAttribute(RequestAttributes.RESPUESTA_SERVICIO, retorno);
					return retorno;
				}
			}
		}
		catch(LoginServiceException e) {
			Object sesionUsuario = e.getSesionUsuario();
			if( sesionUsuario != null ) {
				idSesion = TokenFactory.make();
				byte[] clave = ID_ATRIBUTO_PROXY.getBytes("UTF-8");
				HttpKeyValidator.registrarLoginUsuario(userLogger, request, idSesion, clave, sesionUsuario);
			}
			return e.getSesionUsuario();
		}
		catch(LogoutServiceException e) {
			HttpKeyValidator.procesarLogoutSesion(request, idSesion, codigoUsuario, userLogger);
			HttpKeyValidator.clearRequest(request, userLogger);
			return e.getLogoutRetorno();
		}
		catch(UnidentifiedUserException e) {
			th = new SecurityException();
		}
		catch(InvocationTargetException e) {
			if( e.getCause() != null )
				th = e.getCause();
			else
				th = e;
		}
		if( th != null ) {
			if( throwsException.get() ) {
				throw th;
			}
			else if( userLogger != null ) {
				userLogger.error("Invocando " + servicioSolicitado, th);
			}
			else if( logger != null ) {
				logger.error("Invocando " + servicioSolicitado, th);
			}
		}
		else if( throwsException.get() ) {
			throw new IllegalAccessException("Invocando " + servicioSolicitado);
		}
		return null;
	}

	private QueuedBufferDataConsumer procesarBufferDataProvider(String codigoUsuario, BufferDataProvider bufferDataProvider, Logger debug) throws IOException {
		QueuedBufferDataConsumer consumer = new QueuedBufferDataConsumer();
		bufferDataProvider.setConsumer(consumer);
		request.setAttribute(RequestAttributes.RESPUESTA_SERVICIO, consumer);
		Thread th = new Thread(bufferDataProvider.getRunnableContext());
		th.start();
		if( consumer.hasNext() ) {
			if( debug != null ) {
				int n = consumer.getSize();
				debug.debug("[" + codigoUsuario + "] Generando datos al vuelo " + (n != -1 ? "con " + n + " datos" : ""));
			}
		}
		else {
			if( debug != null ) {
				debug.debug("[" + codigoUsuario + "] Generando datos al vuelo no obtuvo ningun dato");
			}
		}
		return consumer;
	}
}
