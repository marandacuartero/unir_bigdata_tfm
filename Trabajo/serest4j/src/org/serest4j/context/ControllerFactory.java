package org.serest4j.context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.serest4j.annotation.endpoint.TMRedirectController;
import org.serest4j.annotation.service.TMAudit;
import org.serest4j.annotation.service.TMBasicController;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.annotation.service.TMInternal;
import org.serest4j.annotation.service.TMNoWaitResponse;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.ToroidQueue;
import org.serest4j.audit.AuditProcessor;
import org.serest4j.common.GSonFormatter;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.db.TMRelationProcessor;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.idserver.policy.CredencialsInterface;
import org.serest4j.jmx.ControllerEstadisticas;
import org.serest4j.jmx.ControllerRegister;
import org.serest4j.proxy.DirectProxyFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Es la clase mas importante de este api. Es la que se encarga de la construcci�n e invocacion de servicios, la gestion transaccional y de la concurrencia
 * de accesos, asi como de la inyeccion de contexto en los controladores. Tambien ejecuta, si se requiere, la invocacion correspondiente a la auditoria generica
 * solicitada.
 * 
 * @author Maranda
 *
 */
public class ControllerFactory {

	/**
	 * Buffer de controladores correctamente construidos
	 */
	private SortedMap<String, Class<?>> controladores = Collections.synchronizedSortedMap(new TreeMap<String, Class<?>>());
	/**
	 * Buffer de las diversas instancias de los controladores construidos. Su labor es la de almacenar unicamente la cantidad necesaria de controladores y optimizar
	 * de esta forma la cantidad de memoria disponible.
	 */
	private SortedMap<String, ToroidQueue<Object>> instanciasControladores = Collections.synchronizedSortedMap(new TreeMap<String, ToroidQueue<Object>>());
	private SortedMap<String, Object> instanciasSingletons = Collections.synchronizedSortedMap(new TreeMap<String, Object>());
	/**
	 * Buffer de servicios solicitados. Esto permite un acceso mas rapido e inmediato de los servicios, sin necesidad de andar utilizando continuamente el api java.lang.reflect.
	 */
	private SortedMap<String, Method[]> servicios = Collections.synchronizedSortedMap(new TreeMap<String, Method[]>());
	private SortedMap<String, ControllerEstadisticas> accesosServiciosMBean = Collections.synchronizedSortedMap(new TreeMap<String, ControllerEstadisticas>());
	private AtomicBoolean abAccesosServiciosMBean = new AtomicBoolean(false);
	private Logger debug = null;
	private ContextContainerInyector proxyContextoInyector;
	private GSonFormatter gSonFormat;
	private PropertiesLoader propertiesLoader;
	private final String contextKey;
	private final String privateId;

	/**
	 * Reinicia contadores de estadisticas
	 * @param ag
	 * @return
	 */
	private synchronized void resetCounters(String key) {
		if( key == null ) {
			synchronized (accesosServiciosMBean) {
				for( ControllerEstadisticas ce : accesosServiciosMBean.values() ) {
					if( ce != null ) {
						ce.setEstadisticas(null);
						ce.setInstancias(-1);
					}
				}
			}
			servicios.clear();
			controladores.clear();
			instanciasControladores.clear();
			instanciasSingletons.clear();
		}
		else {
			synchronized (accesosServiciosMBean) {
				for( ControllerEstadisticas ce : accesosServiciosMBean.values() ) {
					if( ce != null  &&  key.equals(ce.getControlador()) ) {
						ce.setEstadisticas(null);
						ce.setInstancias(-1);
					}
				}
			}
			servicios.remove(key);
			controladores.remove(key);
			instanciasControladores.remove(key);
			instanciasSingletons.remove(key);
		}
	}

	private class ResetCountersRunnable implements Runnable {
		
		private final String key;
		
		private ResetCountersRunnable(String k) {
			this.key = k;
		}

		@Override
		public void run() {
			resetCounters(key);
		}
	}

	private class BuildEstadisticasRunnable implements Runnable {

		private final StringBuffer sb;

		private BuildEstadisticasRunnable(StringBuffer sb) {
			this.sb = sb;
		}

		@Override
		public void run() {
			sb.setLength(0);
			printFactoriaControladores(sb);
		}
	}

	
	/**
	 * Genera y controla una unica instancia de este objeto, inicializandolo con el objeto que realizara la gestion de auditorias genericas.
	 * @param ag
	 * @return
	 */
	private synchronized void printFactoriaControladores(StringBuffer sb) {
		sb.append("<?xml version='1.0' encoding='UTF-8'?>\n");
		sb.append("<root>\n");
		ArrayList<String> al = new ArrayList<String>(100);
		sb.append("<controladores>\n");
		al.addAll(controladores.keySet());
		for( String key : al ) {
			sb.append("<s key='").append(key);
			Class<?> value = controladores.get(key);
			ToroidQueue<?> instancias = instanciasControladores.get(key);
			if( instancias != null ) {
				sb.append("' ninstancias='").append(instancias.size()).append("' nmax='").append(instancias.elements()).append("'");
			}
			else if( instanciasSingletons.get(key) != null ) {
				sb.append("' ninstancias='singleton'");
			}
			if( value != null  && value.getName().equals(key) ) {
				sb.append(" />\n");
			}
			else if( value != null ) {
				sb.append(">").append(value.getName()).append("</s>\n");
			}
		}
		sb.append("</controladores>\n");
		sb.append("<servicios>\n");
		al.clear();
		al.addAll(servicios.keySet());
		for( String key : al ) {
			Method[] metodos = servicios.get(key);
			if( metodos != null  &&  metodos.length > 0 ) {
				sb.append("<s key='").append(key);
				synchronized (accesosServiciosMBean) {
					ControllerEstadisticas ce = accesosServiciosMBean.get(key);
					if( ce != null ) {
						long[] naccesos = ce.getEstadisticas();
						if( naccesos != null ) {
							sb.append("' n='").append(naccesos[0]).append("' tmin='").append(naccesos[1]);
							sb.append("' tmax='").append(naccesos[2]).append("' tmed='").append(naccesos[3]);
							sb.append("' >\n");
						} else {
							sb.append("' n='0' >\n");
						}
					}
				}
				for( Method m : metodos ) {
					sb.append("<m>");
					sb.append(m);
					sb.append("</m>\n");
				}
				sb.append("</s>\n");
			}
		}
		sb.append("</servicios>\n");
		sb.append("</root>\n");
	}

	private static final HashMap<String, ControllerFactory> instancias = new HashMap<String, ControllerFactory>();

	public static final ControllerFactory buildControllerFactory(String contextKey, PropertiesLoader propertiesLoader, GSonFormatter gson, Logger logger) {
		synchronized(instancias) {
			ControllerFactory controllerFactory = instancias.get(contextKey);
			if( controllerFactory == null ) {
				controllerFactory = new ControllerFactory(contextKey, propertiesLoader, gson, logger);
				instancias.put(contextKey, controllerFactory);
			}
			return controllerFactory;
		}
	}

	public static final ControllerFactory get(String key) {
		synchronized(instancias) {
			return instancias.get(key);	
		}
	}

	private void finalizar(Object obj) {
		try {
			Method m = obj.getClass().getMethod("finalize");
			if( m != null ) {
				m.invoke(obj);
			}
		} catch (NoSuchMethodException e) {
		} catch (SecurityException e) {
		} catch(Throwable th) {th.printStackTrace();}
	}

	public final void destroy() {
		ControllerFactory cf = null;
		synchronized(instancias) {
			cf = instancias.get(contextKey);
			instancias.put(contextKey, null);
			instancias.remove(contextKey);
		}
		if( cf != null  &&  privateId.equals(cf.privateId) ) {
			controladores.clear();
			for( ToroidQueue<?> toroidQueue : instanciasControladores.values() ) {
				Object objctrll = toroidQueue.saca();
				while( objctrll != null ) {
					finalizar(objctrll);
					objctrll = toroidQueue.saca();
				}
			}
			instanciasControladores.clear();
			for( Object sgt : instanciasSingletons.values() ) {
				finalizar(sgt);
			};
			instanciasSingletons.clear();
			servicios.clear();
			abAccesosServiciosMBean.set(false);
			accesosServiciosMBean.clear();

			controladores = null;
			instanciasControladores = null;
			instanciasSingletons = null;
			servicios = null;
			accesosServiciosMBean = null;

			proxyContextoInyector = null;
			gSonFormat = null;
			propertiesLoader = null;
		}
	}

	private ControllerFactory(String contextKey, PropertiesLoader propertiesLoader, GSonFormatter gson, Logger logger) {
		if( logger != null  &&  logger.isDebugEnabled() ) {
			this.debug = logger;
		}
		else {
			this.debug = null;
		}
		this.proxyContextoInyector = new ContextContainerInyector(logger);
		this.gSonFormat = gson;
		this.propertiesLoader = propertiesLoader;
		this.contextKey = contextKey;
		this.privateId = UUID.randomUUID().toString();
		if( this.abAccesosServiciosMBean.compareAndSet(false, true) ) {
			ControllerEstadisticas controllerEstadisticas = new ControllerEstadisticas();
			controllerEstadisticas.setEstadisticas(null);
			controllerEstadisticas.setServiceName("ControllerFactory_init/" + this.contextKey + "/" + this.privateId);
			controllerEstadisticas.setRunnable(new ResetCountersRunnable(null));
			StringBuffer stringBuffer = new StringBuffer();
			BuildEstadisticasRunnable runnable = new BuildEstadisticasRunnable(stringBuffer);
			controllerEstadisticas.setEstatus(stringBuffer, runnable);
			controllerEstadisticas.setInstancias(0);
			if( ControllerRegister.registrar("org.serest4j:type=ControllerFactory" + this.contextKey + ",name=init", controllerEstadisticas, logger, null) ) {
				this.abAccesosServiciosMBean.set(true);
				controllerEstadisticas.setInstancias(1);
			}
		}
	}

	/**
	 * Realiza el procesamiento de la invocacion entrante a traves del servlet ServicioAccesoComunServlet, buscando e invocando el controlador, e inyectandole
	 * el contexto. En este caso, la gesti�n transaccional y el control de commit y rollback corre por cuenta del hilo generado por este mismo proceso.
	 * 
	 * @param codigoUsuario El codigo del usuario que realiza la peticion
	 * @param request El objeto request de la petici�n http del servlet
	 * @param servicioSolicitado El nombre sel servicio solicitado, en la forma 'nombre.completo.del.paquete.NombreDeLaClase.nombreDelServicio'
	 * @param argumentos Los argumentos requeridos para la invocacion del metodo
	 * @return El objeto resultante de realizar la invocacion del servicio. Si el servicio esta marcado como NoEsperes, se realiza la invocacion como un hilo
	 * independiente y se devuelve un null al flujo de datos http de manera inmediata, de forma que el cliente no necesita esperar a la conclusion del mismo.
	 * Si el retorno es de tipo Iterator<E>, se genera un objeto que permite que
	 * el cliente vaya leyendo datos al vuelo, mientras el servidor los va construyendo, sin tener que esperar a la finalizacion completa de todo el proceso. En
	 * cualquier otro caso, el servidor realiza la llamada al servicio, espera la conclusion del mismo, devuelve el objeto construido por el servicio, y cierra la
	 * conexion.
	 * 
	 * @throws InvocationTargetException
	 * @throws IllegalInvocationException 
	 * @throws LoginServiceException 
	 * @throws LogoutServiceException 
	 * 
	 * @see BufferIterator
	 * @see TMAsynchronous
	 * 
	 */
	public Object procesarInvocacion(HttpServletRequest request, String servicioSolicitado, boolean tryFromJson, boolean isInternal, Object[] argumentos) throws IllegalInvocationException, InvocationTargetException, LoginServiceException, LogoutServiceException {
		String mapeoServicio = servicioSolicitado.trim();
		try {
			return _procesarInvocacion(request, mapeoServicio, tryFromJson, isInternal, argumentos);
		} catch (RedirectLinkException e) {
			String msredireccion = e.getNuevoNombreServicio();
			if( debug != null ) {
				debug.trace("Redireccion de " + mapeoServicio + " hacia " + msredireccion);
			}
			ArrayList<String> listaServiciosIntentados = new ArrayList<String>();
			listaServiciosIntentados.add(mapeoServicio);
			listaServiciosIntentados.add(msredireccion);
			try {
				return _bucleProcesarInvocacion(listaServiciosIntentados, request, msredireccion, tryFromJson, isInternal, argumentos);
			} finally {
				if( debug != null ) {
					debug.trace("Mapeo de servicios de " + servicioSolicitado + " hacia " + listaServiciosIntentados.get(listaServiciosIntentados.size() - 1));
				}
				listaServiciosIntentados.clear();
				listaServiciosIntentados = null;
			}
		}
	}

	private Object _bucleProcesarInvocacion(ArrayList<String> listaServiciosIntentados, HttpServletRequest request, String nuevoServicio, boolean tryFromJson, boolean isInternal, Object... argumentos) throws IllegalInvocationException, InvocationTargetException, LoginServiceException, LogoutServiceException {
		try {
			return _procesarInvocacion(request, nuevoServicio, tryFromJson, isInternal, argumentos);
		} catch (RedirectLinkException e) {
			String msredireccion = e.getNuevoNombreServicio();
			if( listaServiciosIntentados.indexOf(msredireccion) != -1 ) {
				// este servicio ya se ha intentado
				if( debug != null ) {
					debug.trace("Bucle cerrado con " + listaServiciosIntentados);
				}
				throw new IllegalInvocationException("Bucle cerrado con " + listaServiciosIntentados);
			}
			if( debug != null ) {
				debug.trace("Redireccion de " + nuevoServicio + " hacia " + msredireccion);
			}
			listaServiciosIntentados.add(msredireccion);
			return _bucleProcesarInvocacion(listaServiciosIntentados, request, msredireccion, tryFromJson, isInternal, argumentos);
		}
	}

	private void updateEstadisticas(String key, long time, String clase, int numero) {
		if( key != null  &&  abAccesosServiciosMBean.get() ) {
			synchronized (accesosServiciosMBean) {
				ControllerEstadisticas controllerEstadisticas = accesosServiciosMBean.get(key);
				ControllerEstadisticas controllerClase = accesosServiciosMBean.get(clase);
				if( controllerEstadisticas == null ) {
					controllerEstadisticas = new ControllerEstadisticas();
					controllerEstadisticas.setEstadisticas(null);
					controllerEstadisticas.setInstancias(-1);
					controllerEstadisticas.setServiceName(key);
					controllerEstadisticas.setControlador(clase);
					controllerEstadisticas.setRunnable(null);
					accesosServiciosMBean.put(key, controllerEstadisticas);
					if( ControllerRegister.registrar("org.serest4j:type=ControllerFactory" + this.contextKey + ",name=" + key, controllerEstadisticas, debug, null) ) {
						if( controllerClase == null ) {
							controllerClase = new ControllerEstadisticas();
							controllerClase.setEstadisticas(null);
							controllerClase.setInstancias(-1);
							controllerClase.setServiceName(clase);
							controllerClase.setControlador(clase);
							controllerClase.setRunnable(new ResetCountersRunnable(clase));
							accesosServiciosMBean.put(clase, controllerClase);
							ControllerRegister.registrar("org.serest4j:type=ControllerFactory" + this.contextKey + ",name=" + clase, controllerClase, debug, null);
						}
					}
				}
				if( controllerClase != null ) {
					controllerClase.setInstancias(numero);	
				}
				if( controllerEstadisticas != null ) {
					long[] value = controllerEstadisticas.getEstadisticas();
					if( value == null ) {
						value = new long[5];
						value[0] = 1l; value[4] = value[3] = value[2] = value[1] = time;
					}
					else {
						value[0] = value[0] + 1;
						value[1] = Math.min(value[1], time);
						value[2] = Math.max(value[2], time);
						value[4] = value[4] + time;
						value[3] = value[4] / value[0];
					}
					controllerEstadisticas.setEstadisticas(value);
				}
			}
		}
	}

	private Object _procesarInvocacion(HttpServletRequest request, String mapeoServicio, boolean tryFromJson, boolean isInternal, Object[] argumentos) throws IllegalInvocationException, RedirectLinkException, InvocationTargetException, LoginServiceException, LogoutServiceException {
		ClassLoader[] classLoader = new ClassLoader[]{null, Thread.currentThread().getContextClassLoader(), getClass().getClassLoader()};
		if( request != null ) {
			classLoader[0] = request.getServletContext().getClassLoader();
		}
		CredencialsInterface[] credenciales = null;
		Logger userLogger = null;
		Object objectUsuario = null;
		if( request != null ) {
			objectUsuario = request.getAttribute(RequestAttributes.USER_SESION);
			credenciales = (CredencialsInterface[])(request.getAttribute(RequestAttributes.USER_CREDENTIALS));
			userLogger = RequestAttributes.getUserLogger(request);
		}
		String servicio = mapeoServicio;//.servicio;
		int io = servicio.lastIndexOf('.');
		String nombreControlador = servicio.substring(0, io);
		io++;
		String nombreServicio = servicio.substring(io);
		Class<?> controlador = getControlador(classLoader, nombreControlador);
		if( controlador != null ) {
			if( !isInternal  &&  !AccessRolesValidator.comprobarControlador(controlador, credenciales, userLogger == null ? debug : userLogger) ) {
				LinkCalculator.nombreServicioEnlaceControlador(mapeoServicio, controlador); // valida una posible redireccion desde los roles de acceso
				throw new IllegalInvocationException("Error gestionando permisos internos invocando " + servicio);
			}
			AtomicReference<String> nombreServicioInvocado = new AtomicReference<String>(null);
			ToroidQueue<Object> repositorioSesiones = instanciasControladores.get(nombreControlador);
			if( repositorioSesiones != null ) {
				int size = repositorioSesiones.size();
				Object sesion = repositorioSesiones.saca();
				if( sesion == null ) {
					if( debug != null ) {
						debug.debug("Controlador " + nombreControlador + " incorpora nueva instancia de " + controlador);
					}
					try {
						sesion = controlador.getDeclaredConstructor().newInstance();
					} catch (InstantiationException e) {
						throw new IllegalInvocationException("Error instanciando controlador del servicio " + servicio + ": " + e.getMessage());
					} catch (IllegalAccessException e) {
						throw new IllegalInvocationException("Error instanciando controlador del servicio " + servicio + ": " + e.getMessage());
					} catch (IllegalArgumentException e) {
						throw new IllegalInvocationException("Error instanciando controlador del servicio " + servicio + ": " + e.getMessage());
					} catch (NoSuchMethodException e) {
						throw new IllegalInvocationException("Error instanciando controlador del servicio " + servicio + ": " + e.getMessage());
					} catch (SecurityException e) {
						throw new IllegalInvocationException("Error instanciando controlador del servicio " + servicio + ": " + e.getMessage());
					}
				}
				else {
					if( debug != null ) {
						debug.trace("Controlador " + nombreControlador + " reutiliza una instancia de " + controlador + "@" + Integer.toHexString(sesion.hashCode()));
					}
				}
				long lsct = System.currentTimeMillis();
				try {
					Object retorno = invocaServicio(nombreServicioInvocado, objectUsuario, controlador, sesion, nombreServicio, request, credenciales, userLogger, tryFromJson, isInternal, argumentos);
					return retorno;
				}finally {
					if( size < 100 ) {
						repositorioSesiones.mete(sesion);
					}
					size = repositorioSesiones.size();
					updateEstadisticas(nombreServicioInvocado.get(), System.currentTimeMillis() - lsct, nombreControlador, size);
				}
			}
			else {
				Object sesion = instanciasSingletons.get(nombreControlador);
				if( sesion != null ) {
					if( debug != null ) {
						debug.trace("Controlador " + nombreControlador + " recupera singleton " + controlador  + "@" + Integer.toHexString(sesion.hashCode()));
					}
					long lsct = System.currentTimeMillis();
					try {
						Object retorno = invocaServicio(nombreServicioInvocado, objectUsuario, controlador, sesion, nombreServicio, request, credenciales, userLogger, tryFromJson, isInternal, argumentos);
						return retorno;
					}finally {
						updateEstadisticas(nombreServicioInvocado.get(), System.currentTimeMillis() - lsct, nombreControlador, 1);
					}
				}
			}
		}
		if( debug != null ) {
			debug.error("Error invocando servicio " + servicio);
		}
		throw new IllegalInvocationException("Error invocando servicio " + servicio);
	}

	private Class<?> getControlador(ClassLoader[] cl, String nombreControlador)
	{
		Class<?> controlador = controladores.get(nombreControlador);
		if( controlador == null ) {
			Object existeSesionInstanciable = null;
			boolean esSingleton = true;
			try {
				controlador = loadClass(cl, nombreControlador);
				if( controlador.isAnnotationPresent(TMInjectableContext.class) ) {
					esSingleton = false;
				}
				else if( controlador.isAnnotationPresent(TMBasicController.class) ) {
				}
				else if( controlador.isInterface() ) {
				}
				else {
					throw new IllegalAccessException("La clase elegida no es un controlador " + nombreControlador);
				}
				if( controlador.isInterface() )
					existeSesionInstanciable = controlador;
				else {
					existeSesionInstanciable = controlador.getDeclaredConstructor().newInstance();
					if( debug != null ) {
						debug.debug("Controlador " + nombreControlador + " genera nueva instancia de " + controlador);
					}
				}
			} catch (Exception e) {
				controlador = null;
				existeSesionInstanciable = null;
				if( debug != null ) {
					debug.error("Error generando controlador " + nombreControlador, e);
				}
				else {
					e.printStackTrace();	
				}
			}
			if( controlador != null ) {
				controladores.put(nombreControlador, controlador);
				if( existeSesionInstanciable != null ) {
					if( esSingleton ) {
						instanciasSingletons.put(nombreControlador, existeSesionInstanciable);
					}
					else {
						ToroidQueue<Object> colaInstancias = new ToroidQueue<Object>();
						colaInstancias.mete(existeSesionInstanciable);
						instanciasControladores.put(nombreControlador, colaInstancias);
					}
					if( debug != null ) {
						debug.trace("Controlador " + nombreControlador + " recupera " + controlador + (esSingleton ? " singleton" : " multiples instancias"));
					}
				}
			}
		}
		return controlador;
	}

	private Class<?> loadClass(ClassLoader[] cl, String nombreControlador) throws ClassNotFoundException {
		ClassNotFoundException excepcion = null;
		for( ClassLoader _cl : cl ) {
			if( _cl != null ) {
				try {
					return _cl.loadClass(nombreControlador);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					excepcion = e;
				}
			}
		}
		if( excepcion != null ) {
			throw excepcion;
		}
		return null;
	}

	private Object invocaServicio(AtomicReference<String> nombreServicioInvocado, Object objectUsuario, Class<?> claseControlador, Object controlador, String nombreServicio,
			HttpServletRequest request, CredencialsInterface[] credenciales, Logger userLogger, boolean tryFromJson, boolean isInternal, Object[] argumentos) throws InvocationTargetException, RedirectLinkException, IllegalInvocationException, LoginServiceException, LogoutServiceException
			{
		String keyServicio = claseControlador.getName() + "." + nombreServicio;
		int nargumentos = argumentos == null ? 0 : argumentos.length;
		keyServicio = keyServicio + "_" + nargumentos;
		nombreServicioInvocado.set(keyServicio);
		Method m[] = servicios.get(keyServicio);
		if( m == null  ||  m.length <= 0 ) {
			// construyo e invoca la primera vez
			ArrayList<Method> al = new ArrayList<Method>();
			obtenMetodos(al, claseControlador, nombreServicio, nargumentos);
			m = new Method[al.size()];
			m = al.toArray(m);
			servicios.put(keyServicio, m);
			if( debug != null ) {
				debug.trace("Generado servicio " + keyServicio + " en " + al);
			}
		}
		if( m != null  &&  m.length > 0 ) {
			// verifico el metodo
			boolean esAuditable = false;
			boolean esIterable = false;
			boolean esNoEsperes = false;
			boolean esServicioDeLogin = false;
			boolean esServicioDeLogut = false;
			Object objetoRetorno = null;
			// invoco
			try {
				Method metodoElegido = nargumentos == 0 ? m[0] : buscaMetodo(m, tryFromJson, argumentos);
				esServicioDeLogin = AccessRolesValidator.comprobarLogin(metodoElegido, debug);
				esServicioDeLogut = AccessRolesValidator.comprobarLogout(metodoElegido, debug);
				if( !isInternal  &&  !esServicioDeLogut  &&  !esServicioDeLogin  &&  !AccessRolesValidator.comprobarMetodo(metodoElegido, credenciales, userLogger == null ? debug : userLogger) ) {
					LinkCalculator.nombreServicioEnlaceMetodo(keyServicio, metodoElegido); // valida una posible redireccion desde los roles de acceso
					throw new IllegalInvocationException("Error gestionando permisos internos de metodo " + metodoElegido);
				}
				String nombreRedireccion = esRedireccionable(claseControlador, metodoElegido);
				try {
					LinkCalculator.nombreServicioEnlace(metodoElegido); // valida una posible redireccion
				}catch(RedirectLinkException redirectLinkException) {
					if( nombreRedireccion != null ) {
						keyServicio = redirectLinkException.getNuevoNombreServicio();
					}
					else {
						throw redirectLinkException;
					}
				}
				if( nombreRedireccion == null  &&  claseControlador == controlador )
					throw new IllegalAccessException("La clase elegida es un interfaz y no se puede instanciar " + controlador);
				if( !isInternal ) {
					esUsoInterno(metodoElegido);
				}
				esAuditable = !esServicioDeLogin  &&  !esServicioDeLogut  && esAuditable(metodoElegido);
				esIterable = esIterable(metodoElegido);
				esNoEsperes = esNoEsperes(metodoElegido);
				objetoRetorno = procesamientoConjunto(controlador, nombreServicio, request, objectUsuario, keyServicio, nombreRedireccion, esAuditable, esIterable, esNoEsperes, metodoElegido, userLogger, argumentos);
			} catch (IllegalInvocationException e) {
				throw e;
			} catch (RedirectLinkException e) {
				throw e;
			} catch (InvocationTargetException e) {
				throw e;
			} catch (Exception e) {
				if( debug != null ) {
					debug.debug("Error invocando servicio " + keyServicio + " en " + Arrays.deepToString(m) + " con " + Arrays.deepToString(argumentos) + " " + e);
					debug.error("Error invocando servicio " + keyServicio, e);
				}
				throw new IllegalInvocationException("Error invocando servicio " + keyServicio + " en " + Arrays.deepToString(m) + " con " + Arrays.deepToString(argumentos));
			}
			finally {
				if( esAuditable ) {
					AuditProcessor.auditarAuditoriaGenerica(objectUsuario, objetoRetorno, claseControlador.getName(), nombreServicio, argumentos);
				}
			}
			if( esServicioDeLogin ) {
				AuditProcessor.auditarAuditoriaGenerica(objetoRetorno, objetoRetorno, claseControlador.getName(), nombreServicio, argumentos);
				throw new LoginServiceException(objetoRetorno);
			}
			else if( esServicioDeLogut ) {
				AuditProcessor.auditarAuditoriaGenerica(objectUsuario, objetoRetorno, claseControlador.getName(), nombreServicio, argumentos);
				throw new LogoutServiceException(objetoRetorno);
			}
			return objetoRetorno;
		}
		if( debug != null ) {
			debug.error("Error invocando servicio " + keyServicio+ ", no se encontraron metodos asociados al mismo");
		}
		throw new IllegalInvocationException("Error invocando servicio " + keyServicio+ ", no se encontraron metodos asociados al mismo");
			}

	private boolean cumpleNumeroParametros(Method m, int nargumentos) {
		Class<?>[] argmClass = m.getParameterTypes();
		int nargmClass = argmClass == null ? 0 : argmClass.length;
		return nargumentos == nargmClass;
	}

	private void obtenMetodos(List<Method> listaMetodos, Class<?> claseControlador, String nombreServicio, int nargumentos) {
		Method[] metodos = claseControlador.getMethods();
		for( Method _m : metodos ) {
			if( _m.getName().equals(nombreServicio)  &&  cumpleNumeroParametros(_m, nargumentos) ) {
				if( _m.getDeclaringClass().equals(claseControlador) ) {
					listaMetodos.add(_m);
				}
				else if( TMRelationProcessor.class.isAssignableFrom(claseControlador) ) {
					listaMetodos.add(_m);
				}
			}
		}
		Class<?> clasePadre = claseControlador.getSuperclass();
		if( clasePadre != null  &&  clasePadre != Object.class ) {
			if( clasePadre.isAnnotationPresent(TMInjectableContext.class)  ||  clasePadre.isAnnotationPresent(TMBasicController.class) ) {
				obtenMetodos(listaMetodos, clasePadre, nombreServicio, nargumentos);
			}
		}
		Class<?>[] interfaces = claseControlador.getInterfaces();
		for( Class<?> interfaz : interfaces ) {
			obtenMetodos(listaMetodos, interfaz, nombreServicio, nargumentos);
		}
	}

	private Method buscaMetodo(Method[] metodos, boolean tryFromJson, Object[] argumentos) {
		if( metodos.length > 1  ||  tryFromJson ) {
			Object[] argumentos2 = Arrays.copyOf(argumentos, argumentos.length);
			for( Method m : metodos ) {
				Class<?>[] clases = m.getParameterTypes();
				int nargumentos = argumentos.length;
				boolean asignable = true;
				for( int i=0; asignable  &&  i<nargumentos; i++ ) {
					if( argumentos[i] == null ) {}
					else if( clases[i].equals(argumentos[i].getClass()) ){}
					else if( tryFromJson  &&  argumentos[i] instanceof String  &&  !clases[i].equals(String.class) ) {
						asignable = false;
						try {
							Object convertido = gSonFormat.fromJson(argumentos[i].toString(), clases[i]);
							if( esPrimitivoCompatible(clases[i], convertido) ) {
								argumentos2[i] = convertido;
								asignable = true;
							}
							else {
								argumentos2[i] = convertido;
								asignable = true;
							}
						} catch(Throwable th) {
							if( debug != null ) {
								debug.trace("buscaMetodo " + m, th);
							}
						}
					}
					else if( esPrimitivoCompatible(clases[i], argumentos[i]) ) {}
					else if( esArrayCompatible(clases[i], argumentos[i]) ) {}
					else if( clases[i].isInstance(argumentos[i]) ){}
					else if( clases[i].isAssignableFrom(argumentos[i].getClass()) ) {}
					else {
						asignable = false;
					}
				}
				if( asignable ) {
					if( tryFromJson ) {
						System.arraycopy(argumentos2, 0, argumentos, 0, nargumentos);
					}
					return m;
				}
			}
		}
		return metodos[0];
	}

	private boolean esPrimitivoCompatible(Class<?> c, Object obj) {
		if( c.isPrimitive()  &&  obj != null ) {
			if( c == Boolean.TYPE ) {
				return obj instanceof Boolean;
			} else if( c == Character.TYPE ) {
				return obj instanceof Character;
			} else if( c == Byte.TYPE ) {
				return obj instanceof Byte;
			} else if( c == Short.TYPE ) {
				return obj instanceof Short  ||  obj instanceof Byte;
			} else if( c == Integer.TYPE ) {
				return obj instanceof Integer  ||  obj instanceof Short  ||  obj instanceof Byte;
			} else if( c == Long.TYPE ) {
				return obj instanceof Long  ||  obj instanceof Integer  ||  obj instanceof Short  ||  obj instanceof Byte;
			} else if( c == Float.TYPE ) {
				return obj instanceof Float  ||  obj instanceof Integer  ||  obj instanceof Short  ||  obj instanceof Byte;
			} else if( c == Double.TYPE ) {
				return obj instanceof Double  ||  obj instanceof Float  ||  obj instanceof Integer  ||  obj instanceof Short  ||  obj instanceof Byte;
			}
		}
		return false;
	}

	private boolean esArrayCompatible(Class<?> c, Object obj) {
		if( obj == null ) {
			return true;
		}
		else if( c.isArray()  &&  obj.getClass().isArray() ) {
			Class<?> tipo = c.getComponentType();
			if( tipo.equals(obj.getClass().getComponentType()) ) {
				return true;
			}
			else if( tipo.isAssignableFrom(obj.getClass().getComponentType()) ) {
				return true;
			}
			else {
			}
		}
		return false;
	}

	private boolean esAuditable(Method m) {
		return m != null  &&  m.isAnnotationPresent(TMAudit.class);
	}

	private boolean esIterable(Method m) {
		return m != null  &&  m.getReturnType().isInterface()  &&  m.getReturnType().isAssignableFrom(Iterator.class);
	}

	private boolean esNoEsperes(Method m) {
		return m != null  &&  m.isAnnotationPresent(TMNoWaitResponse.class);
	}

	private void esUsoInterno(Method m) throws IllegalInvocationException {
		if( m != null  &&  m.isAnnotationPresent(TMInternal.class) ) {
			if( debug != null ) {
				debug.error("Acceso restringido en " + m.toString());
			}
			throw new IllegalInvocationException("Acceso restringido en " + m.toString());
		}
	}

	private String esRedireccionable(Class<?> c, Method metodoElegido) {
		if( c.isInterface()  &&  metodoElegido.isAnnotationPresent(TMRedirectController.class) ) {
			String str = metodoElegido.getAnnotation(TMRedirectController.class).value().trim();
			if( str.length() > 0 ) {
				return str;
			}
		}
		else if( c.isInterface()  &&  c.isAnnotationPresent(TMRedirectController.class) ) {
			String str = c.getAnnotation(TMRedirectController.class).value().trim();
			if( str.length() > 0 ) {
				return str;
			}
		}
		return null;
	}

	private Object copiaInstancia(Object obj) {
		Object retorno = null;
		try {
			retorno = obj.getClass().getDeclaredConstructor().newInstance();
		} catch (Throwable th) {
			if( debug != null ) {
				debug.error("COMO ES POSIBLE??? " + obj, th);
			}
			th.printStackTrace();
			retorno = null;
		}
		return retorno;
	}

	private Object procesamientoConjunto(Object controlador, String nombreServicio, HttpServletRequest request, Object sesionUsuario, String keyServicio, String redireccion,
			boolean esAuditable, boolean esIterable, boolean esNoEsperes, Method metodoElegido, Logger userLogger,
			Object[] argumentos) throws Exception {
		RequestAttributes.addBreadCrumbsServletContext(request);
		Object objetoRetorno = null;
		if( redireccion != null ) {
			if( esNoEsperes ) {
				InvocadorMetodoRemotoRunnable invocadorMetodoRemotoRunnable = new InvocadorMetodoRemotoRunnable(redireccion,
						esAuditable, keyServicio, nombreServicio, sesionUsuario, ((Class<?>)controlador).getName(), argumentos);
				Thread th = new Thread(invocadorMetodoRemotoRunnable);
				th.setPriority(Thread.MIN_PRIORITY + 1);
				th.setDaemon(true);
				th.start();
				objetoRetorno = null; 
			}
			else {
				try {
					DirectProxyFactory directProxyFactory = ServerStaticContext.get(contextKey).getDirectProxyFactory(redireccion);
					if( argumentos != null  &&  argumentos.length > 0 ) {
						objetoRetorno = directProxyFactory.procesarPeticion(keyServicio, argumentos);
					}
					else {
						objetoRetorno = directProxyFactory.procesarPeticion(keyServicio);
					}
					if( objetoRetorno instanceof Iterator ) {
						Iterator<?> iterator = ((Iterator<?>)objetoRetorno);
						BufferDataProvider bufferDataProvider = new BufferDataProvider();
						IteratorConsumerBrigde iteratorConsumerBrigde = new IteratorConsumerBrigde(iterator, bufferDataProvider, debug);
						bufferDataProvider.setRunnableContext(iteratorConsumerBrigde);
						objetoRetorno = bufferDataProvider;
					}
				} catch (Throwable e) {
					throw new InvocationTargetException(e);
				}
			}
		}
		else if( !esIterable  &&  !esNoEsperes ) {
			TMContext proxyContexto = proxyContextoInyector.inyectaContexto(controlador, nombreServicio, propertiesLoader, request, userLogger);
			try {
				if( argumentos != null  &&  argumentos.length > 0 ) {
					objetoRetorno = metodoElegido.invoke(controlador, argumentos);	
				}
				else {
					objetoRetorno = metodoElegido.invoke(controlador);
				}
			} catch (InvocationTargetException e) {
				if( proxyContexto != null ) {
					proxyContexto.clear(e.getCause());
					proxyContexto = null;
				}
				throw e;
			}
			catch (Exception e) {
				if( proxyContexto != null ) {
					Throwable ecause = e.getCause();
					proxyContexto.clear(ecause == null ? e : ecause);
				}
				throw e;
			}
			if( proxyContexto != null ) {
				proxyContexto.clear(null);
			}
		}
		else {
			controlador = copiaInstancia(controlador);
			if( controlador != null ) {
				if( esNoEsperes ) {
					TMContext proxyContexto = proxyContextoInyector.inyectaContexto(controlador, nombreServicio, propertiesLoader, request, userLogger);
					Runnable r = new InvocadorMetodoRunnable(proxyContexto, esAuditable, keyServicio, nombreServicio, sesionUsuario, metodoElegido, controlador, argumentos);
					Thread th = new Thread(r);
					th.setPriority(Thread.NORM_PRIORITY - 1);
					th.setDaemon(true);
					th.start();
					objetoRetorno = null;
				}
				else if( esIterable ) {
					TMContext proxyContexto = proxyContextoInyector.inyectaContexto(controlador, nombreServicio, propertiesLoader, request, userLogger);
					if( proxyContexto != null ) {
						proxyContexto.setOutput(new BufferDataProvider());
						Runnable r = new InvocadorMetodoRunnable(proxyContexto, esAuditable, keyServicio, nombreServicio, sesionUsuario, metodoElegido, controlador, argumentos);
						proxyContexto.getOutput().setRunnableContext(r);
						objetoRetorno = proxyContexto.getOutput();
					}
				}
			}
		}
		return objetoRetorno;
	}

	private class InvocadorMetodoRunnable implements Runnable {
		TMContext proxyContexto;
		Method m;
		Object controlador;
		Object[] argumentos;
		boolean esAuditable;
		String keyServicio;
		String nombreServicio;
		Object objectUsuario;

		InvocadorMetodoRunnable(TMContext proxyContexto, boolean esAuditable,
				String keyServicio, String nombreServicio, Object objectUsuario,
				Method m, Object controlador, Object[] argumentos) {
			this.proxyContexto = proxyContexto;
			this.m = m;
			this.controlador = controlador;
			this.argumentos = argumentos;
			this.esAuditable = esAuditable;
			this.keyServicio = keyServicio;
			this.nombreServicio = nombreServicio;
			this.objectUsuario = objectUsuario;
		}

		public void run() {
			Object retorno = null;
			try {
				retorno = _run();
				if( debug != null ) {
					debug.debug("Iterator procesado en " + keyServicio + " en " + m + " >> " + retorno);
				}
			} catch (Throwable th) {
				if( proxyContexto != null ) {
					proxyContexto.clear(th);
					proxyContexto = null;
				}
				if( debug != null ) {
					debug.debug("Error invocando servicio " + keyServicio + " en " + m + " con " + Arrays.deepToString(argumentos));
				}
			}
			finally {
				if( esAuditable ) {
					AuditProcessor.auditarAuditoriaGenerica(objectUsuario, retorno, controlador.getClass().getName(), nombreServicio, argumentos);
				}
				if( proxyContexto != null ) {
					proxyContexto.clear(null);
					proxyContexto = null;
				}
			}
		}		

		public Object _run() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			if( argumentos != null  &&  argumentos.length > 0 ) {
				return m.invoke(controlador, argumentos);
			}
			else {
				return m.invoke(controlador);
			}
		}
	}

	private class InvocadorMetodoRemotoRunnable implements Runnable {
		String redireccion;
		String controlador;
		Object[] argumentos;
		boolean esAuditable;
		String keyServicio;
		String nombreServicio;
		Object objectUsuario;

		InvocadorMetodoRemotoRunnable(String redireccion,
				boolean esAuditable, String keyServicio, String nombreServicio, Object objectUsuario,
				String controlador, Object[] argumentos) {
			this.redireccion = redireccion;
			this.controlador = controlador;
			this.argumentos = argumentos;
			this.esAuditable = esAuditable;
			this.keyServicio = keyServicio;
			this.nombreServicio = nombreServicio;
			this.objectUsuario = objectUsuario;
		}

		public void run() {
			Object retorno = null;
			try {
				retorno = _run();
				if( debug != null ) {
					debug.debug("Procesando en remoto " + keyServicio + " para " + redireccion +  " >> " + retorno);
				}
				if( retorno instanceof Iterator ) {
					Iterator<?> iterator = ((Iterator<?>)retorno);
					retorno = null;
					new IteratorConsumerBrigde(iterator, null, debug).run();
				}
			} catch (Throwable th) {
				if( debug != null ) {
					debug.debug("Error invocando en remoto " + keyServicio + " para " + redireccion + " con " + Arrays.deepToString(argumentos));
				}
			}
			finally {
				if( esAuditable ) {
					AuditProcessor.auditarAuditoriaGenerica(objectUsuario, retorno, controlador, nombreServicio, argumentos);
				}
			}
		}

		public Object _run() throws Throwable {
			DirectProxyFactory directProxyFactory = ServerStaticContext.get(contextKey).getDirectProxyFactory(redireccion);
			if( argumentos != null  &&  argumentos.length > 0 ) {
				return directProxyFactory.procesarPeticion(keyServicio, argumentos);
			}
			else {
				return directProxyFactory.procesarPeticion(keyServicio);
			}
		}
	}

	private class IteratorConsumerBrigde implements Runnable {

		private final Iterator<?> iterator;
		private final BufferDataProvider output;
		private final Logger logger;

		public IteratorConsumerBrigde(Iterator<?> iterator, BufferDataProvider output, Logger logger) {
			this.iterator = iterator;
			this.output = output;
			this.logger = logger;
		}

		@Override
		public void run() {
			if( logger != null ) {
				logger.debug("Consumiendo iterator con " + output);
			}
			Iterator<?> it = iterator;
			while( it != null  &&  it.hasNext() ) {
				Object obj = it.next();
				if( obj != null  &&  output != null ) {
					try {
						output.send(obj);
					} catch (Exception e) {
						it.remove();
						it = null;
						if( logger != null ) {
							logger.debug("Redireccionando iterator hacia " + output, e);
						}
					}
				}
			}
			try {
				if( output != null ) {
					output.close();	
				}
			} catch (Exception e) {
				if( logger != null ) {
					logger.debug("Redireccionando iterator hacia " + output, e);
				}
			}
			if( logger != null ) {
				logger.debug("Iterator consumido");
			}
		}
	}
}
