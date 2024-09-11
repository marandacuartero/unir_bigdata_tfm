package org.serest4j.http.server;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.DirectOutputDataConsumer;
import org.serest4j.async.JsonDataConsumer;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.common.ContentType;
import org.serest4j.common.FileLogger;
import org.serest4j.common.GSonFormatter;
import org.serest4j.context.ControllerFactory;
import org.serest4j.context.LoginServiceException;
import org.serest4j.context.LogoutServiceException;
import org.serest4j.context.TMReturnContextException;
import org.serest4j.cripto.TokenFactory;
import org.serest4j.http.ExtendTimeInResponse;
import org.serest4j.http.HttpResponseErrorCode;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.UnidentifiedUserException;
import org.serest4j.http.idserver.HttpKeyValidator;
import org.serest4j.http.idserver.KeyContainer;
import org.serest4j.http.idserver.policy.CredencialsInterface;
import org.serest4j.http.idserver.policy.CredentialsType;
import org.serest4j.http.idserver.policy.UserDescriptorInstance;
import org.serest4j.http.rest.RestQueryInfo;
import org.serest4j.http.rest.RestQueryInterpreter;
import org.serest4j.http.rest.RestServicesMapping;
import org.serest4j.jmx.ControllerEstadisticas;
import org.serest4j.jmx.ControllerRegister;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class TMRestServer extends HttpServlet {

	private ControllerFactory factoriaControladores;
	private GSonFormatter gSonFormat;
	private RestQueryInterpreter conversorQueryToArguments;
	private int segundosDeDemoraAnteAccesoIlegal = 0;
	private byte[] notUsedClave;
	private CredencialsInterface[] credencialsInterfacesSinUsuario = null;
	private UserDescriptorInstance userDescriptorInstance = null;
	private String cookieDomain = null;
	private AtomicReference<TMBuildEstadisticasRunnable> arControllerEstadisticas = new AtomicReference<TMBuildEstadisticasRunnable>(null);
	private AtomicBoolean abRegistrado = new AtomicBoolean(false);
	private String servletName = "";
	private String nombrePrevio = null;

	public TMRestServer() {
		super();
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

	public void configureServlet(UserDescriptorInstance userDescriptorInstances, CredencialsInterface[] credencialsInterfacesSinUsuario,
			ControllerFactory factoriaControladores,
			RestServicesMapping restServicesMapping, GSonFormatter gSonFormat, int segundosDeDemoraAnteAccesoIlegal, String cookieDomain) {
		this.gSonFormat = gSonFormat;
		this.factoriaControladores = factoriaControladores;
		this.userDescriptorInstance = userDescriptorInstances;
		this.conversorQueryToArguments = new RestQueryInterpreter(restServicesMapping, 100);
		this.credencialsInterfacesSinUsuario = credencialsInterfacesSinUsuario;
		this.segundosDeDemoraAnteAccesoIlegal = segundosDeDemoraAnteAccesoIlegal;
		this.notUsedClave = getClass().toString().getBytes();
		if( cookieDomain != null  &&  cookieDomain.trim().length() > 0 ) {
			this.cookieDomain = cookieDomain.trim();
		}
	}

	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		boolean procesaAsincrono = request.isAsyncSupported();
		if( abRegistrado.compareAndSet(false, true) ) {
			registrarJMX(request.getContextPath(), request.getServletPath(), procesaAsincrono);
		}
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
			_service(null, request, response, logger);
		}
	}

	private Logger trace(Logger logger, Logger userLogger) {
		Logger _logger = userLogger == null ? logger : userLogger;
		if( _logger != null  &&  !_logger.isTraceEnabled() ) {
			_logger = null;
		}
		return _logger;
	}

	private boolean _service(AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response, Logger logger) throws ServletException, IOException
	{
		long tiempoProcesamiento = System.currentTimeMillis();
		String contextoServlet = request.getServletPath();
		try {
			String nombreServicio = null;
			RestQueryInfo conversorQueryHeader = null;
			try {
				conversorQueryHeader = conversorQueryToArguments.convertir(request.getServletPath(), request, logger.isDebugEnabled() ? logger : null);
				nombreServicio = conversorQueryHeader.getServicioSistema();
				nombreServicio.charAt(0);
			}
			catch(Exception e) {
				logger.error("Error en servlet ", e);
				throw new BadAccessException("Detectado intento de acceso no valido");
			}
			Object[] argumentos = (Object[])(conversorQueryHeader.getArgumentos());
			if( logger.isDebugEnabled() ) {
				logger.debug("Nombre del servicio " + nombreServicio);
				logger.debug("Argumentos " + Arrays.toString(argumentos));
			}
			Object respuesta = null;
			String codigoUsuario = null;
			Logger userLogger = null;
			String idClave = HttpKeyValidator.getIdSesion(request, logger.isDebugEnabled() ? logger : null);
			KeyContainer contenedorClaves = HttpKeyValidator.sacarClave(request.getContextPath(), idClave);
			if( contenedorClaves != null ) {
				if( argumentos != null  &&  argumentos.length > 0  &&  idClave.equals(argumentos[0]) ) {
					Object[] aux = new Object[argumentos.length - 1];
					if( aux.length > 0 ) {
						System.arraycopy(argumentos, 1, aux, 0, aux.length);
					}
					argumentos = aux;
				}
			}
			else {
				idClave = null;
				if( argumentos != null  &&  argumentos.length > 0 ) {
					String _idClave = argumentos[0].toString();
					contenedorClaves = HttpKeyValidator.sacarClave(request.getContextPath(), _idClave);
					if( contenedorClaves != null ) {
						idClave = new String(_idClave);
						if( logger.isDebugEnabled() ) {
							logger.debug("Obtenido parametro idClave=" + idClave);
						}
						Object[] aux = new Object[argumentos.length - 1];
						if( aux.length > 0 ) {
							System.arraycopy(argumentos, 1, aux, 0, aux.length);
						}
						argumentos = aux;
						if( logger.isDebugEnabled() ) {
							logger.debug("Nuevos Argumentos " + Arrays.toString(argumentos));
						}
					}
				}
			}
			boolean cumpleCredencialesSinLogin = userDescriptorInstance.comprobarCredencialesServicio(null, nombreServicio, logger);
			boolean cumpleCredencialesConLogin = false;
			if( contenedorClaves == null ) {
				idClave = null;
				codigoUsuario = null;
				if( !cumpleCredencialesSinLogin )
					throw new UnidentifiedUserException("[" + idClave + "] No es una sesion valida");
			}
			else {
				Object sesionUsuario = HttpKeyValidator.existeUsuarioLogeado(contenedorClaves);
				if( sesionUsuario == null ) {
					idClave = null;
					codigoUsuario = null;
					if( !cumpleCredencialesSinLogin )
						throw new UnidentifiedUserException("[" + idClave + "] No es una sesion valida");
				}
				else {
					codigoUsuario = contenedorClaves.getCodigoUsuario();
					cumpleCredencialesConLogin = userDescriptorInstance.comprobarCredencialesServicio(sesionUsuario, nombreServicio, logger); 
					if( idClave != null ) {
						HttpKeyValidator.setIdSesionInResponse(response, idClave, cookieDomain);
					}
				}
			}
			if( cumpleCredencialesSinLogin  ||  cumpleCredencialesConLogin ) {
				// debo dejar pasar si el usuario tiene permisos, o si se esta intentando loguear
				if( codigoUsuario != null ) {
					request.setAttribute(RequestAttributes.ID_SESION, idClave);
					request.setAttribute(RequestAttributes.USER_CODE, codigoUsuario);
					request.setAttribute(RequestAttributes.USER_SESION, contenedorClaves.getInformacionUsuario());
					request.setAttribute(RequestAttributes.USER_CREDENTIALS, contenedorClaves.getCredencialesUsuario());
					userLogger = RequestAttributes.getUserLogger(request);
				}
				boolean servicioDeLogin = false;
				if( logger.isDebugEnabled() ) {
					logger.debug("[" + codigoUsuario + "] Servicio " + nombreServicio + "(" + Arrays.toString(argumentos) + ")");
				}
				try {
					respuesta = factoriaControladores.procesarInvocacion(request, nombreServicio, true, false, argumentos);
				}
				catch(LoginServiceException th) {
					servicioDeLogin = true;
					respuesta = th.getSesionUsuario();
				}
				catch(LogoutServiceException th) {
					HttpKeyValidator.procesarLogoutSesion(request, idClave, codigoUsuario, trace(logger, userLogger));
					HttpKeyValidator.clearRequest(request, trace(logger, userLogger));
					respuesta = th.getLogoutRetorno();
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
				if( logger.isDebugEnabled() ) {
					StringBuilder sb = new StringBuilder();
					sb.append('[').append(codigoUsuario).append("] Genera ").append(respuesta);
					if( sb.length() > 1024 ) {
						sb.setLength(1000);
						sb.append("...\n");
					}
					logger.debug(sb.toString());
				}
				if( respuesta != null  &&  respuesta instanceof TMReturnContextException ) {
					TMReturnContextException rcte = (TMReturnContextException)respuesta;
					respuesta = rcte.getRetorno();
					if( respuesta == null )
						respuesta = rcte.getCause();
				}
				else if( codigoUsuario != null  &&  servicioDeLogin ) {
					// ya estoy logueado, si alguien se esta intentando volver a loguear sobre esta misma conexion
					throw new BadLoginException();
				}
				else if( codigoUsuario == null  &&  servicioDeLogin ) {
					idClave = TokenFactory.make();
					contenedorClaves = HttpKeyValidator.registrarLoginUsuario(logger, request, idClave, notUsedClave, respuesta);
					if( contenedorClaves != null ) {
						codigoUsuario = contenedorClaves.getCodigoUsuario();
						request.setAttribute(RequestAttributes.ID_SESION, idClave);
						request.setAttribute(RequestAttributes.USER_CODE, codigoUsuario);
						request.setAttribute(RequestAttributes.USER_SESION, contenedorClaves.getInformacionUsuario());
						request.setAttribute(RequestAttributes.USER_CREDENTIALS, contenedorClaves.getCredencialesUsuario());
						userLogger = RequestAttributes.getUserLogger(request);
						HttpKeyValidator.setIdSesionInResponse(response, idClave, cookieDomain);
					}
					else {
						// he fallado en el intento de login
						logger.debug("Detectado intento de login fallido.");
						throw new BadLoginException(); //("Detectado intento de login reincidente fallido.");
					}
				}
			}
			String nombreServicioForward = normalizar(conversorQueryHeader.getForward(), request, RequestAttributes.FORWARD_NAME);
			String nombreServicioForwardType = normalizar(conversorQueryHeader.getTipoForward(), request, RequestAttributes.FORWARD_TYPE);
			String nombreContentType = normalizar(conversorQueryHeader.getContentType(), request, RequestAttributes.CONTENT_TYPE);
			String nombreContentName = normalizar(conversorQueryHeader.getContentName(), request, RequestAttributes.CONTENT_NAME);
			nombreContentName = extractName(nombreContentName, argumentos);
			if( nombreServicioForward != null  && "redirect".equalsIgnoreCase(nombreServicioForwardType) ) {
				if( logger.isDebugEnabled() ) {
					logger.debug("[" + codigoUsuario + "] Redirect to " + request.getContextPath() + nombreServicioForward + ", respuesta=" + respuesta);
				}
				response.sendRedirect(request.getContextPath() + nombreServicioForward);
				return true;
			}
			else if( nombreServicioForward != null  &&  comprobarCredencialesDominio(userLogger, request, nombreServicioForward.toString(), codigoUsuario) ) {
				if( logger.isDebugEnabled() ) {
					logger.debug("[" + codigoUsuario + "] Forward (" + nombreServicioForwardType + ") to " + nombreServicioForward + ", respuesta=" + respuesta);
				}
				request.setAttribute(RequestAttributes.RESPUESTA_SERVICIO, respuesta);
				if( respuesta != null  &&  respuesta instanceof BufferDataProvider ) {
					BufferDataProvider bufferDataProvider = (BufferDataProvider)respuesta;
					QueuedBufferDataConsumer consumer = new QueuedBufferDataConsumer();
					bufferDataProvider.setConsumer(consumer);
					request.setAttribute(RequestAttributes.RESPUESTA_SERVICIO, consumer);
					Thread th = new Thread(bufferDataProvider.getRunnableContext());
					th.start();
					if( consumer.hasNext() ) {
						if( logger.isDebugEnabled() ) {
							int n = consumer.getSize();
							logger.debug("[" + codigoUsuario + "] Generando datos al vuelo " + (n != -1 ? "con " + n + " datos" : ""));
						}
					}
					else {
						if( logger.isDebugEnabled() ) {
							logger.debug("[" + codigoUsuario + "] Generando datos al vuelo no obtuvo ningun dato");
						}
					}
				}
				if( asyncContext == null ) {
					if( "include".equalsIgnoreCase(nombreServicioForwardType) )
						request.getRequestDispatcher(nombreServicioForward.toString()).include(request, response);
					else
						request.getRequestDispatcher(nombreServicioForward.toString()).forward(request, response);
					HttpKeyValidator.clearRequest(request, trace(logger, userLogger));
					return true;
				}
				else {
					if( "include".equalsIgnoreCase(nombreServicioForwardType) ) {
						request.getRequestDispatcher(nombreServicioForward.toString()).include(request, response);
						return true;
					}
					else {
						asyncContext.dispatch(nombreServicioForward.toString());
						return false;
					}
				}
			}
			else if( respuesta != null  &&  respuesta instanceof Throwable ) {
				printThrowable((Throwable)respuesta, logger);
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			else if( respuesta != null  &&  respuesta instanceof BufferDataProvider ) {
				BufferDataProvider bufferDataProvider = (BufferDataProvider)respuesta;
				if( nombreContentType != null  &&  !isJsonDirect(nombreContentType) ) {
					DirectOutputDataConsumer directOutputDataConsumer = new DirectOutputDataConsumer(response);
					directOutputDataConsumer.setContent(nombreContentType, nombreContentName);
					bufferDataProvider.setConsumer(directOutputDataConsumer);
				}
				else {
					bufferDataProvider.setConsumer(new JsonDataConsumer(response, gSonFormat));
				}
				Thread th = new Thread(bufferDataProvider.getRunnableContext());
				th.start();
				while( th.isAlive() ) {
					if( logger.isDebugEnabled() ) {
						logger.debug("[" + codigoUsuario + "] Enviando datos al vuelo...");
					}
					try{ th.join(10000l); }catch(Exception e){}
				}
			}
			else {
				if( nombreContentType != null  &&  !isJsonDirect(nombreContentType) ) {
					response.setContentType(nombreContentType);
					if( respuesta == null ) {
						response.setContentLength(0);
					}
					else {
						if( nombreContentName != null ) {
							StringBuilder sb = new StringBuilder();
							sb.append("attachment;filename=").append('"');
							sb.append(nombreContentName);
							sb.append('"');
							response.setHeader("Content-Disposition", sb.toString());
						}
						if( respuesta instanceof CharSequence ) {
							String strRespuesta = respuesta.toString();
							response.setContentLength(strRespuesta.length());
							response.getOutputStream().print(strRespuesta);
						}
						else {
							byte[] b = (byte[])respuesta;
							response.setContentLength(b.length);
							response.getOutputStream().write((byte[])respuesta);
						}
					}
				}
				else {
					response.setContentType("text/x-json");
					response.setCharacterEncoding("UTF-8");
					String strRespuesta = gSonFormat.toJson(respuesta);
					if( logger.isDebugEnabled() ) {
						logger.debug("[" + codigoUsuario + "] Enviando " + strRespuesta);
					}
					response.getOutputStream().write(strRespuesta.getBytes("UTF-8"));
				}
			}
		} catch(BadAccessException aire) {
			ExtendTimeInResponse.procesar(segundosDeDemoraAnteAccesoIlegal);
			if( aire instanceof BadLoginException )
				response.sendError(HttpResponseErrorCode.SESION_NO_VALIDA);
			else
				response.sendError(HttpResponseErrorCode.ERROR_EN_LA_PETICION);
		} catch ( UnidentifiedUserException se) {
			logger.error(se.getMessage(), se);
			response.sendError(HttpResponseErrorCode.SESION_NO_VALIDA);
		} catch(BadCredentialsException iae) {
			logger.error(iae.getMessage(), iae);
			ExtendTimeInResponse.procesar(segundosDeDemoraAnteAccesoIlegal);
			response.sendError(HttpResponseErrorCode.CREDENCIALES_INSUFICIENTES);
		} catch (Throwable th) {
			printThrowable(th, logger);
			ExtendTimeInResponse.procesar(segundosDeDemoraAnteAccesoIlegal);
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
			response.getOutputStream().close();
		}
		return true;
	}

	private boolean isJsonDirect(String nombreContentType) {
		return ContentType.APPLICATION_JSON.equalsIgnoreCase(nombreContentType);
	}

	private void printThrowable(Throwable th, Logger logger) {
		if( th instanceof TMReturnContextException ) {
			TMReturnContextException rcte = (TMReturnContextException)th;
			logger.error("Error en servlet " + rcte.getMinimaTraza(2));
			if( rcte.getCause() != null ) {
				printThrowable(rcte.getCause(), logger);
			}
		}
		else {
			logger.error("Error en servlet ", th);
		}
	}

	private String normalizar(String valorPrevio, HttpServletRequest request, String nombreAtributo) {
		Object obj = request.getAttribute(nombreAtributo);
		if( obj != null ) {
			String str = String.valueOf(obj).trim();
			if( str.length() > 0 ) {
				return str;
			}
		}
		return valorPrevio;
	}

	private boolean comprobarCredencialesDominio(Logger debug, HttpServletRequest request, String pathDominio, String codigoUsuario) throws BadCredentialsException, UnidentifiedUserException {
		String dominio = pathDominio.trim();
		if( !dominio.toLowerCase().startsWith(request.getContextPath().toLowerCase()) ) {
			dominio = request.getContextPath() + "/" + dominio;
			dominio = dominio.replaceAll("//", "/");
		}
		if( credencialsInterfacesSinUsuario != null ) {
			for( CredencialsInterface credencialesUsuario : credencialsInterfacesSinUsuario ) {
				if( credencialesUsuario != null  &&  credencialesUsuario.isValid(CredentialsType.DOMINIO)  &&  credencialesUsuario.comprobarCredenciales(debug, dominio) ) {
					return true;
				}
			}
		}
		if( codigoUsuario == null ) {
			throw new UnidentifiedUserException("El usuario no esta logueado");
		}
		CredencialsInterface[] credencialesUsuarios = (CredencialsInterface[])(request.getAttribute(RequestAttributes.USER_CREDENTIALS));
		if( credencialesUsuarios != null ) {
			for( CredencialsInterface credencialesUsuario : credencialesUsuarios ) {
				if( credencialesUsuario != null  &&  credencialesUsuario.isValid(CredentialsType.DOMINIO)  &&  credencialesUsuario.comprobarCredenciales(debug, dominio) ) {
					return true;
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append('[').append(codigoUsuario).append(']').append(' ');
		sb.append("Credenciales insuficientes: ").append("Acceso a dominio ").append(dominio).append(" ilegal. ");
		throw new BadCredentialsException(sb.toString());
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

		public InternalRunnable(){}

		@Override
		public void run() {
			boolean completar = true;
			try {
				completar = _service(ac, request, response, logger);
			} catch (ServletException e) {
				logger.error("En contexto asincrono", e);
			} catch (Exception e) {
				logger.error("En contexto asincrono", e);
			}
			finally {
				if( completar ) {
					if( logger.isDebugEnabled() ) {
						logger.debug(" Finalizando contexto asincrono");
					}
					try{ ac.complete(); }catch(Exception e){}
				}
				ac = null;
				request = null;
				response = null;
			}
		}
	}

	private String extractName(String str, Object[] argumentos) {
		if( str != null ) {
			str = str.trim();
			if( str.startsWith("{") ) {
				str = str.substring(1);
				if( str.endsWith("}") ) {
					str = str.substring(0, str.length() - 1).trim();
					try {
						String pref = "";
						int io = str.indexOf(':');
						if( io != -1 ) {
							pref = str.substring(0, io).trim();
							io++;
							str = str.substring(io).trim();
						}
						int i = Integer.parseInt(str);
						str = argumentos[i].toString().trim();
						return pref + str;
					}
					catch(Throwable th){}
				}
			}
		}
		return str;
	}
}
