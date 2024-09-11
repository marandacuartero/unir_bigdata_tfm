package org.serest4j.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.SerializedDataConsumer;
import org.serest4j.common.ContentType;
import org.serest4j.common.FileLogger;
import org.serest4j.context.ControllerFactory;
import org.serest4j.context.LoginServiceException;
import org.serest4j.cripto.Clarifier;
import org.serest4j.cripto.FlowUtility;
import org.serest4j.cripto.NoiseFactory;
import org.serest4j.cripto.TokenFactory;
import org.serest4j.http.HttpResponseErrorCode;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.idserver.HttpKeyValidator;
import org.serest4j.http.idserver.policy.CredencialsInterface;
import org.serest4j.http.idserver.policy.TrueCredentials;
import org.serest4j.http.rest.ProxyPairs;
import org.serest4j.jmx.ControllerEstadisticas;
import org.serest4j.jmx.ControllerRegister;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class TMDirectProxyServer extends HttpServlet {

	private static final AtomicInteger contadorProxy = new AtomicInteger(1);
	private static final AtomicLong contadorSesionesProxy = new AtomicLong(1l);
	private ControllerFactory factoriaControladores;
	private CredencialsInterface[] credenciales;
	private String codigoUsuario;
	private Map<String, ProxyPairs> tokenProxies;
	private AtomicReference<TMBuildEstadisticasRunnable> arControllerEstadisticas = new AtomicReference<TMBuildEstadisticasRunnable>(null);
	private AtomicBoolean abRegistrado = new AtomicBoolean(false);
	private String servletName = "";
	private String nombrePrevio = null;

	public TMDirectProxyServer() {
		super();
	}

	public void configureServlet(ControllerFactory factoriaControladores, Map<String, ProxyPairs> tokens) {
		this.factoriaControladores = factoriaControladores;
		this.credenciales = new CredencialsInterface[]{ new TrueCredentials() };
		this.codigoUsuario = "proxy_" + contadorProxy.getAndIncrement();
		this.tokenProxies = tokens;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		this.servletName = config.getServletName();
		registrarJMX(config.getServletContext().getContextPath(), this.servletName, false);
	}

	private void registrarJMX(String contexto, String path, boolean asincrono) {
		Logger logger = FileLogger.getLogger(contexto);
		ControllerEstadisticas controllerEstadisticas = new ControllerEstadisticas();
		controllerEstadisticas.setEstadisticas(null);
		controllerEstadisticas.setInstancias(-1);
		controllerEstadisticas.setControlador(getClass().getName() + "/" + servletName);
		controllerEstadisticas.setServiceName(contexto + "/" + path);
		String keyNombrePrevio = "org.serest4j:type=RestService" + contexto + ",name=" + path;
		if( ControllerRegister.registrar(keyNombrePrevio, controllerEstadisticas, logger, nombrePrevio) ) {
			nombrePrevio = keyNombrePrevio;
			StringBuffer stringBuffer = new StringBuffer();
			TMBuildEstadisticasRunnable runnable = new TMBuildEstadisticasRunnable(stringBuffer, controllerEstadisticas);
			controllerEstadisticas.setEstatus(stringBuffer, runnable);
			arControllerEstadisticas.set(runnable);
		}
	}

	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		boolean procesaAsincrono = request.isAsyncSupported();
		if( abRegistrado.compareAndSet(false, true) ) {
			registrarJMX(request.getContextPath(), request.getServletPath(), procesaAsincrono);
		}
		if( !"POST".equalsIgnoreCase(request.getMethod()) ) {
			response.setContentType(ContentType.TEXT_PLAIN);
			response.getWriter().println("***************************************************");
			response.getWriter().println(request.getRemoteAddr() + ":" + request.getRemotePort());
			response.getWriter().println(request.getRemoteHost() + ":" + request.getRemotePort());
			response.getWriter().println("***************************************************");
			response.getWriter().println(request.getLocalAddr() + ":" + request.getLocalPort());
			response.getWriter().println(request.getLocalName() + ":" + request.getLocalPort());
			response.getWriter().println("***************************************************");
			String xFf = request.getHeader("X-Forwarded-For");
			if( xFf != null  &&  xFf.trim().length() > 0 ) {
				response.getWriter().println("X-Forwarded-For=" + xFf);
				response.getWriter().println("***************************************************");	
			}
			response.getWriter().println(request.getLocale());
			response.getWriter().println("***************************************************");
			response.getWriter().println(request.getMethod());
			response.getWriter().println(request.getContextPath());
			response.getWriter().println(request.getServletPath());
			if( request.getQueryString() != null ) {
				response.getWriter().println(request.getQueryString());
			}
			response.getWriter().flush();
		}
		else {
			Logger logger = FileLogger.getLogger(request.getContextPath());
			if( procesaAsincrono ) {
				AsyncContext ac = request.startAsync(request, response);
				logger.debug("Generando contexto asincrono");
				InternalRunnable internalRunnable = new InternalRunnable();
				internalRunnable.ac = ac;
				internalRunnable.request = request;
				internalRunnable.logger = logger;
				internalRunnable.response = response;
				Thread th = new Thread(internalRunnable);
				th.setDaemon(true);
				LocalRunnable localRunnable = new LocalRunnable();
				localRunnable.th = th;
				ac.setTimeout(-1);
				ac.start(localRunnable);
			}
			else {
				_service(request, response, logger);
			}
		}
	}

	private void _service(HttpServletRequest request, HttpServletResponse response, Logger logger) throws ServletException, IOException
	{
		long tiempoProcesamiento = System.currentTimeMillis();
		String contextoServlet = request.getServletPath();
		try {
			// si se solicita servicio para JSON, permito que el sistema realice una busqueda mas profunda del servicio, intentando
			// las conversiones b�sicas de String a Primitivo y de String en formato Json al objeto correspondiente
			Object[] datos = null;
			String idClave = null;
			byte[] tokenClave = tokenProxies.get(contextoServlet).getTokenClave();
			try {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				FlowUtility.flushStream(request.getInputStream(), bout);
				byte[][] b = Clarifier.desencripta1(bout);
				idClave = new String(b[0]);
				if( !TokenFactory.verify(idClave) )
					throw new Exception();
				datos = Clarifier.desencripta2(tokenClave, b[1]);
			}
			catch(Throwable throwable) {
				datos = null;
				logger.trace("Existe algun error sospechoso en la estructura de los datos", throwable);
				throw new BadAccessException("Existe algun error sospechoso en la estructura de los datos");
			}
			Object respuesta = null;
			if( datos != null  &&  datos.length > 0 ) {
				String nombreServicio = datos[0].toString();
				if( nombreServicio == null  ||  nombreServicio.isEmpty() ) {
					throw new BadAccessException("Error invocando el servicio: Nombre de servicio vacio.");
				}
				else {
					if( !tokenProxies.get(contextoServlet).containsController(nombreServicio.substring(0, nombreServicio.lastIndexOf('.'))) ) {
						throw new BadAccessException("Error invocando el servicio: Servicio no publicado en " + tokenProxies.get(contextoServlet).listControllers());
					}
					request.setAttribute(RequestAttributes.ID_SESION, idClave);
					request.setAttribute(RequestAttributes.USER_CODE, codigoUsuario);
					request.setAttribute(RequestAttributes.USER_SESION, codigoUsuario + ":" + contadorSesionesProxy.getAndIncrement());
					request.setAttribute(RequestAttributes.USER_CREDENTIALS, credenciales);
					int n = datos.length - 1;
					Object[] argumentos = new Object[n];
					for( int i=0; i<n; i++ ) {
						argumentos[i] = datos[i + 1];
					}
					if( logger != null  &&  logger.isDebugEnabled() ) {
						logger.debug("[Proxy] Servicio " + nombreServicio + "(" + Arrays.toString(argumentos) + ")");
					}
					try {
						respuesta = factoriaControladores.procesarInvocacion(request, nombreServicio, false, true, argumentos);
					}
					catch(LoginServiceException th) {
						respuesta = th.getSesionUsuario();
					}
					catch(InvocationTargetException th) {
						if( th.getCause() != null ) {
							printThrowable(th.getCause(), logger);
							respuesta = th.getCause();
						}
						else {
							throw th;
						}
					}
					if( logger != null  &&  logger.isDebugEnabled() ) {
						logger.debug("[Proxy] Genera " + respuesta);
					}
				}
				Arrays.fill(datos, null);
			}
			enviarRespuesta(new ObjectOutputStream(response.getOutputStream()), respuesta, tokenClave, logger);
		} catch(BadAccessException aire) {
			logger.error(aire.getMessage());
			response.sendError(HttpResponseErrorCode.ERROR_EN_LA_PETICION);
		} catch (Throwable th) {
			printThrowable(th, logger);
			response.sendError(HttpResponseErrorCode.ERROR_EN_LA_PETICION);
		}
		finally {
			TMBuildEstadisticasRunnable tmBuildEstadisticasRunnable = arControllerEstadisticas.get();
			if( tmBuildEstadisticasRunnable != null ) {
				long time = System.currentTimeMillis() - tiempoProcesamiento;
				tmBuildEstadisticasRunnable.build(time, contextoServlet);
			}
		}
		HttpKeyValidator.clearRequest(request, logger.isTraceEnabled() ? logger : null);
		if( !response.isCommitted() ) {
			response.getOutputStream().flush();
			response.getOutputStream().close();
		}
	}

	private void printThrowable(Throwable th, Logger logger ) {
		if( th != null  &&  th.getCause() != null ) {
			printThrowable(th.getCause(), logger);
		}
		else {
			logger.error("Error en servlet ", th);
		}
	}

	private void enviarRespuesta(ObjectOutputStream out, Object respuesta, byte[] clave, Logger logger) throws Throwable {
		if( respuesta != null  &&  respuesta instanceof BufferDataProvider ) {
			BufferDataProvider bufferDataProvider = (BufferDataProvider)respuesta;
			bufferDataProvider.setConsumer(new SerializedDataConsumer(out, clave));
			Thread th = new Thread(bufferDataProvider.getRunnableContext());
			th.start();
			while( th.isAlive() ) {
				if( logger != null  &&  logger.isDebugEnabled() ) {
					logger.debug("[" + codigoUsuario + "] Enviando respuesta al vuelo... ");
				}
				try{ th.join(10000l); }catch(Exception e){}
			}
			bufferDataProvider.close();
		}
		else {
			String[] strDatos = null;
			Object[] bufferDatosRespuesta = new Object[1];
			bufferDatosRespuesta[0] = respuesta;
			strDatos = NoiseFactory.encriptaChunked(null, clave, bufferDatosRespuesta, logger);
			if( strDatos != null ) {
				for( String utf : strDatos ) {
					out.writeUTF(utf);	
				}
			}
		}
		out.flush();
	}

	private class LocalRunnable implements Runnable {

		Thread th;

		@Override
		public void run() {
			try {
				th.start();
			} catch (Throwable e) {
				e.printStackTrace();
			}
			th = null;
		}		
	}

	private class InternalRunnable implements Runnable {

		private AsyncContext ac;
		private HttpServletRequest request;
		private HttpServletResponse response;
		private Logger logger;

		@Override
		public void run() {
			try {
				_service(request, response, logger);
			} catch (ServletException e) {
				logger.error("En contexto asincrono", e);
			} catch (IOException e) {
				logger.error("En contexto asincrono", e);
			}
			finally {
				logger.debug(" Finalizando contexto asincrono");
				ac.complete();
				ac = null;
				request = null;
				response = null;
			}
		}
	}
}
