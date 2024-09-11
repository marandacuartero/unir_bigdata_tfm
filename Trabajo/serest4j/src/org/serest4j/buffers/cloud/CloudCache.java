package org.serest4j.buffers.cloud;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataProvider;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.buffers.CacheLogger;
import org.serest4j.buffers.LightWeightCache;
import org.serest4j.buffers.StrongWeigthCacheManager;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.common.Version;
import org.serest4j.context.ServerStaticContext;
import org.serest4j.jmx.CacheEstadisticas;
import org.serest4j.jmx.ControllerRegister;
import org.serest4j.proxy.DirectProxyFactory;

import jakarta.servlet.ServletContext;

public abstract class CloudCache<E> extends LightWeightCache<E> {

	private final ArrayList<String> destinos = new ArrayList<String>();
	private final ArrayList<String> destinosNoAccesibles = new ArrayList<String>();
	private final AtomicReference<Object> inicializado = new AtomicReference<Object>(null);
	private final AtomicBoolean balanceada = new AtomicBoolean(false);
	private final AtomicBoolean asincrona = new AtomicBoolean(false);
	private final String idCache;
	private final String contexto;
	private final String thisClassCacheName;
	private final HashMap<String, DirectProxyFactory> hmCloud = new HashMap<String, DirectProxyFactory>();
	private byte[] tokenClaveCloudCache = new byte[0];

	protected CloudCache(String contexto, String idCache, StrongWeigthCacheManager<E> manager) {
		super(manager);
		this.idCache = idCache;
		this.contexto = contexto;
		this.thisClassCacheName = this.getClass().getName();
		validateInstance();
		initLogger();
	}

	public String toString() {
		return getClass().getName() + "_" + idCache + "." + hashCode();
	}

	final String getAndValidateIdCache(String idCacheRemota) {
		if( !idCacheRemota.equals(idCache) ) {
			if( !idCachesActivas.contains(idCacheRemota) ) { // me viene una peticion de una cache que no tengo en mis listados
				FileLogger.getLogger(contexto).trace(idCacheRemota + " Solicita reactivacion de conexiones");
				generateLoadDataTask(100l);
			}
		}
		return idCache;
	}

	private class CloudCacheEstadisticasRunnable implements Runnable {

		private final StringBuffer resultado;
		private final StringBuilder metodo;
		private final List<String> argumentos;

		private CloudCacheEstadisticasRunnable(StringBuffer sb, StringBuilder metodo, List<String> argumentos) {
			this.resultado = sb;
			this.metodo = metodo;
			this.argumentos = argumentos;
		}

		@Override
		public void run() {
			resultado.setLength(0);
			if( "listar".equals(metodo.toString()) ) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				list(pw, "true".equals(argumentos.get(0)));
				pw.flush();
				sw.flush();
				resultado.append(sw.getBuffer());
			}
			else if( "clearFrom".equals(metodo.toString()) ) {
				List<?> lista = clearBufferFrom(Long.parseLong(argumentos.get(0)));
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				pw.println("Removed Elements:");
				for( Object e : lista ) {
					pw.println(e);
				}
				pw.println();
				list(pw, false);
				pw.flush();
				sw.flush();
				resultado.append(sw.getBuffer());
			}
			else if( "updateServer".equals(metodo.toString()) ) {
				addUrlCloudCache(argumentos.get(0));
				resultado.append(destinos.toString());
			}
			else if( "removeServer".equals(metodo.toString()) ) {
				removeUrlCloudCache(argumentos.get(0));
				resultado.append(destinos.toString());
			}
		}
	}

	protected void initJMX() {
		CacheEstadisticas cacheEstadisticas = getCacheJMX();
		if( cacheEstadisticas != null ) {
			if( ControllerRegister.registrar("org.serest4j:type=Cache" + this.contexto + ",name=" + getClass().getName(), cacheEstadisticas, FileLogger.getLogger(), null) ) {
				StringBuffer resultado = new StringBuffer();
				StringBuilder metodo = new StringBuilder();
				List<String> argumentos = Collections.synchronizedList(new ArrayList<String>());
				CloudCacheEstadisticasRunnable cacheEstadisticasRunnable = new CloudCacheEstadisticasRunnable(resultado, metodo, argumentos);
				cacheEstadisticas.setProcessor(cacheEstadisticasRunnable, argumentos, metodo, resultado);
			}
		}
	}

	private void validateInstance() { // Con esto evitamos que se puedan generar instancias independientes que no sean a traves de CloudCacheRepository
		if( !CloudCacheRepository.validar(contexto, idCache) ) {
			throw new IllegalArgumentException(getClass().toString() + " instancia no permitida!!");
		}
	}

	private void initLogger() {
		Logger logger = FileLogger.getLogger(contexto);
		if( logger.isTraceEnabled() ) {
			CacheLogger cacheLogger = new CacheLogger() {
				@Override
				public void trace(String str) {
					FileLogger.getLogger(contexto).trace(str);
				}
			};
			super.setLogger(cacheLogger);			
		}
		else {
			super.setLogger(null);
		}
	}

	private synchronized void putDirectProxyFactory(String urlServidor, byte[] tokenClave, Logger logger) {
		String key = normaliceUrl(urlServidor);
		DirectProxyFactory directProxyFactory = hmCloud.get(key);
		if( directProxyFactory == null ) {
			directProxyFactory = DirectProxyFactory.newInstance(tokenClave, logger, new String[]{urlServidor});
			hmCloud.put(key, directProxyFactory);
		}
	}

	private synchronized CloudCacheProxy getDirectProxyFactory(String urlServidor) {
		DirectProxyFactory directProxyFactory = hmCloud.get(normaliceUrl(urlServidor));
		if( directProxyFactory != null ) {
			return directProxyFactory.getProxy(CloudCacheProxy.class);
		}
		return null;
	}

	private String normaliceUrl(String str) {
		if( str != null ) {
			char[] c1 = str.toCharArray();
			int n = c1.length;
			char[] c2 = new char[n];
			int j = 0;
			for( int i=0; i<n; i++ ) {
				if( Character.isLetterOrDigit(c1[i]) ) {
					c2[j++] = c1[i];
				}
				else if( !Character.isWhitespace(c1[i]) ) {
					c2[j++] = '_';
				}
			}
			return new String(c2, 0, j);
		}
		return "";
	}

	void initCloudCache(ServletContext context, String nombreFicheroRedundancia, boolean balanceada, boolean asincrona) {
		if( inicializado.compareAndSet(null, "") ) {
			String strTipo = balanceada ? " Balanceada," : "";
			strTipo = strTipo + (asincrona ? " Asincrona," : "");
			strTipo = strTipo.trim();
			if( strTipo.length() > 0 ) strTipo = strTipo.substring(0, strTipo.length() - 1);
			getCacheJMX().setTipo(strTipo.trim());
			this.balanceada.compareAndSet(false, balanceada);
			this.asincrona.compareAndSet(false, asincrona);
			PropertiesLoader propertiesLoader = ServerStaticContext.get(context.getContextPath()).getPropertiesLoader();
			Logger trace = FileLogger.getLogger(context.getContextPath());
			String token = propertiesLoader.getProperty("serest4j.cloud.token", Version.VALUE);
			byte[] tokenClave = PropertiesLoader.token2bytes(context, token, propertiesLoader, trace);
			this.tokenClaveCloudCache = tokenClave;
			inicializado.set(tokenClave);
			trace.trace("Cache " + getClass().getName() + " instanciada!!! " + toString());
			ArrayList<String> al = new ArrayList<String>();
			if( nombreFicheroRedundancia != null ) {
				HighAvailabilityFileLoader.loadFile(al, nombreFicheroRedundancia, context, propertiesLoader, trace);	
			}
			TreeSet<String> ts = new TreeSet<String>(al);
			al.clear();
			al.addAll(ts);
			ts.clear();
			ts = null;
			for( String urlServidor : al ) {
				putDirectProxyFactory(urlServidor, tokenClave, trace);
			}
			synchronized (destinos) {
				destinos.clear();
				destinos.addAll(al);
			}
			generateLoadDataTask(1000l);
		}
	}

	void addUrlCloudCache(String urlServidor) {
		Logger trace = FileLogger.getLogger(contexto);
		trace.trace("Add URL to Cache " + getClass().getName() + ": " + urlServidor);
		putDirectProxyFactory(urlServidor, tokenClaveCloudCache, trace);
		synchronized (destinos) {
			TreeSet<String> ts = new TreeSet<String>(destinos);
			ts.add(urlServidor);
			ts.addAll(destinosNoAccesibles);
			destinosNoAccesibles.clear();
			destinos.clear();
			destinos.addAll(ts);
			ts.clear();
			ts = null;
		}
		generateLoadDataTask(1000l);
	}

	void removeUrlCloudCache(String urlServidor) {
		FileLogger.getLogger(contexto).trace("Remove URL from Cache " + getClass().getName() + ": " + urlServidor);
		synchronized (destinos) {
			destinos.addAll(destinosNoAccesibles);
			destinosNoAccesibles.clear();
			ArrayList<String> al = new ArrayList<String>();
			for( String _url : destinos ) {
				if( _url.equalsIgnoreCase(urlServidor) ) {}
				else {
					al.add(_url);
				}
			}
			destinos.clear();
			destinos.addAll(al);
		}
		generateLoadDataTask(1000l);
	}

	private static final Long FRECUENCIA_RECARGA_CONEXIONES = Long.valueOf(2l * 60l * 1000l);
	
	private static final Object FRECUENCIA_RECARGA_CONEXIONES_LOCK = new Object();

	private void generateLoadDataTask(long delay) {
		synchronized (FRECUENCIA_RECARGA_CONEXIONES_LOCK) {
			if( loadDataTask == null )
				loadDataTask = new LoadDataTask();
			else
				loadDataTask = loadDataTask.clonarYCancelar();
			super.schedule(loadDataTask, delay, FRECUENCIA_RECARGA_CONEXIONES);
		}
	}

	private LoadDataTask loadDataTask = null;
	private String[] conexionesActivas = null;

	private int sizeDestinos() {
		return copiarConexionesDestino().length;
	}

	private String[] copiarConexionesDestino() {
		synchronized (destinos) {
			if( conexionesActivas == null ) {
				String[] pc = new String[destinos.size()];
				pc = destinos.toArray(pc);
				conexionesActivas = pc;
				super.getCacheJMX().setServidores("Activos: " + destinos + ", Inactivos: " + destinosNoAccesibles);
				super.getCacheJMX().setSize(size());
			}
			return conexionesActivas;
		}
	}

	private final SortedSet<String> idCachesActivas = Collections.synchronizedSortedSet(new TreeSet<String>());

	private class LoadDataTask extends TimerTask {

		private AtomicBoolean loadData = new AtomicBoolean(true);
		private AtomicBoolean ab = new AtomicBoolean(false);

		@Override
		public void run() {
			try {
				_run();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		private void _run() throws Throwable {			
			if( !ab.compareAndSet(false, true) ) {
				return;
			}
			TreeSet<String> ts_destinos = new TreeSet<String>();
			synchronized (destinos) {
				ts_destinos.addAll(destinos);
				ts_destinos.addAll(destinosNoAccesibles);
				destinosNoAccesibles.clear();
				if( ts_destinos.size() <= 0 ) {
					getCacheJMX().setServidores("");
					getCacheJMX().setSize(size());
					return;
				}
			}
			Logger logger = FileLogger.getLogger(contexto);
			TreeSet<String> ts_destinos_repetidos = new TreeSet<String>();
			HashMap<String, String> hm = new HashMap<String, String>();
			for( String urlServicio : ts_destinos ) {
				String idCacheRemota = null;
				try {
					idCacheRemota = getDirectProxyFactory(urlServicio).ping(idCache, thisClassCacheName);
					if( idCacheRemota != null ) { // es la misma cache en mi local
						if( logger.isTraceEnabled() ) {
							logger.trace(urlServicio + " Ping obtiene " + idCacheRemota);
						}
						if( idCacheRemota.equals(idCache) ) {
							idCacheRemota = null;
							ts_destinos_repetidos.add(urlServicio); // estoy accediendo a mi misma maquina
						}
						else if( hm.containsKey(idCacheRemota) ) {
							ts_destinos_repetidos.add(urlServicio);  // esa cache ya me la he traido
							idCacheRemota = null;
						}
						else {
							hm.put(idCacheRemota, urlServicio);
						}
					}
					if( idCacheRemota != null  &&  idCacheRemota.length() > 0 ) {
						if( loadData.get() ) {
							if( logger.isTraceEnabled() ) {
								logger.trace(urlServicio + " Load Data");
							}
							Iterator<?> cdi = getDirectProxyFactory(urlServicio).receiveLoadData(idCache, thisClassCacheName);
							if( logger.isTraceEnabled() ) {
								int size = (cdi instanceof QueuedBufferDataConsumer) ? ((QueuedBufferDataConsumer)cdi).getSize() : -1;
								logger.trace(urlServicio + " Load Data. Recibiendo " + size + " datos...");
							}
							while( cdi.hasNext() ) {
								Object e = cdi.next();
								if( e != null ) {
									noTriggerPut(parseElement(e));
									triggerReceivedNuevo(parseElement(e));
								}
							}
							loadData.set(false);
						}
					}
				}catch (Throwable th) {
					logger.error(urlServicio + " Load Data", th);
					destinosNoAccesibles.add(urlServicio);
					ts_destinos_repetidos.add(urlServicio);
				}
			}
			idCachesActivas.clear();
			idCachesActivas.add(idCache);
			idCachesActivas.addAll(hm.keySet());
			synchronized (destinos) {
				logger.info("********* elimino " + ts_destinos_repetidos);	
				ts_destinos.removeAll(ts_destinos_repetidos);
				destinos.clear();
				destinos.addAll(ts_destinos);
				ts_destinos.clear();
				ts_destinos.addAll(hm.values());
				String[] pc = new String[ts_destinos.size()];
				pc = ts_destinos.toArray(pc);
				conexionesActivas = pc;
				getCacheJMX().setServidores("Activos: " + destinos + ", Inactivos: " + destinosNoAccesibles);
				getCacheJMX().setSize(size());
			}
			hm.clear();
			hm = null;
			logger.error("Proceso de regeneracion de conexiones.\nIds Activos " + idCachesActivas + "\nConexiones Activas " + ts_destinos + "\nConexiones Totales " + destinos);
		}

		LoadDataTask clonarYCancelar() {
			ab.compareAndSet(false, true);
			cancel();
			LoadDataTask _loadDataTask = new LoadDataTask();
			_loadDataTask.loadData.set(this.loadData.get());
			return _loadDataTask;
		}
	}

	protected Logger getTrace() {
		Logger logger = FileLogger.getLogger(contexto);
		return logger.isTraceEnabled() ? logger : null;
	}

	/**
	 * Triggers que se ejecutan solo la maquina local
	 * @param e
	 */
	protected void triggerDeleteLocal(E e){}

	protected void triggerUpdateLocal(E previo, E nuevo){}

	protected void triggerNuevoLocal(E e){}

	/**
	 * Triggers que se ejecutan cuando se recibe una actualizacion desde otra maquina
	 * @param e
	 */
	protected void triggerReceivedDelete(E e){}

	protected void triggerReceivedUpdate(E previo, E nuevo){}

	protected void triggerReceivedNuevo(E e){}

	@Override
	public E get(E key) {
		E e = super.get(key);
		if( e == null ) {
			e = generarSolicitudRemota(key);
			if( e != null ) {
				noTriggerPut(e);
				return e;
			}
		}
		return e;
	}

	private E noTriggerGet(E key) {
		return super.get(key);
	}

	private E generarSolicitudRemota(E key) {
		if( sizeDestinos() > 0 ) {
			Logger trace = getTrace();
			if( trace != null ) {
				trace.trace("No existe la sesion, propago la solicitud para " + key);
			}
			List<String> _destinos = Arrays.asList(copiarConexionesDestino());
			Collections.shuffle(_destinos);
			for( String urlServicio : _destinos ) {
				try {
					if( trace != null ) {
						trace.trace(urlServicio + " GenerarNotificacion " + key);
					}
					Object retorno = getDirectProxyFactory(urlServicio).receiveSolicitudRemota(idCache, thisClassCacheName, key);
					if( trace != null ) {
						trace.trace(urlServicio + " GenerarNotificacion: Obtiene " + retorno);
					}
					if( retorno != null ) {
						E _e = parseElement(retorno);
						if( _e != null  &&  balanceada.get() ) {
							strongTriggerDeleteInterno(_e, true);
						}
						return _e;
					}
				}catch (Throwable th) {
					if( trace != null ) {
						trace.error(urlServicio + " GenerarNotificacion " + key, th);	
					}
				}
			}
		}
		return null;
	}

	protected <T> void generarInvocacionRemota(CloudCacheRemoteListener<T> proxyListener, Logger trace) {
		if( sizeDestinos() > 0  &&  inicializado.get() != null ) {
			List<String> _destinos = Arrays.asList(copiarConexionesDestino());
			Collections.shuffle(_destinos);
			byte[] tokenClave = (byte[])(inicializado.get());
			boolean continuar = true;
			for( String urlServicio : _destinos ) {
				if( continuar ) {
					DirectProxyFactory directProxyFactory = DirectProxyFactory.newInstance(tokenClave, trace, new String[]{urlServicio});
					if( directProxyFactory != null ) {
						try {
							T proxy = directProxyFactory.getProxy(proxyListener.getProxyName());
							if( proxyListener.processResponse(proxy) ) {
								continuar = false;
							}
						}catch (Throwable th) {
							if( trace != null ) {
								trace.error(urlServicio + " GenerarInvocacionRemota", th);
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private E parseElement(Object obj) {
		return (E)obj;
	}

	@Override
	public final void triggerDelete(E e, boolean interno) {
		triggerDeleteLocal(e);
		if( !interno  &&  sizeDestinos() > 0 ) {
			DeleteRunnable deleteRunnable = new DeleteRunnable(null);
			deleteRunnable.e = e;
			start(deleteRunnable, asincrona.get());
		}
	}

	@Override
	public final void triggerUpdate(E previo, E nuevo) {
		triggerUpdateLocal(previo, nuevo);
		if( sizeDestinos() > 0 ) {
			if( balanceada.get() ) {
				UpdateRunnable updateRunnable = new UpdateRunnable(null);
				updateRunnable.previo = previo;
				updateRunnable.nuevo = nuevo;
				start(updateRunnable, asincrona.get());
			}
			else {
				DeleteRunnable deleteRunnable = new DeleteRunnable(null);
				deleteRunnable.e = previo;
				start(deleteRunnable, asincrona.get());
			}
		}
	}

	@Override
	public final void triggerNuevo(E e) {
		triggerNuevoLocal(e);
		if( sizeDestinos() > 0 ) {
			if( balanceada.get() ) {
				NuevoRunnable nuevoRunnable = new NuevoRunnable(null);
				nuevoRunnable.e = e;
				start(nuevoRunnable, asincrona.get());
			}
			else {
				DeleteRunnable deleteRunnable = new DeleteRunnable(null);
				deleteRunnable.e = e;
				start(deleteRunnable, asincrona.get());
			}
		}
	}

	private void start(ClonRunnable cr, boolean envioAsincrono) {
		String[] arrurl = copiarConexionesDestino();
		int n = arrurl == null ? 0 : arrurl.length;
		if( n > 0 ) {
			Thread[] arrth = new Thread[n];
			int p = Thread.currentThread().getPriority() + 1;
			for( int i=0; i<n; i++ ) {
				if( arrurl[i] != null ) {
					arrth[i] = new Thread(cr.clonar(arrurl[i]));
					arrth[i].setPriority(p);
				}
			}
			for( int i=0; i<n; i++ ) { if( arrth[i] != null ) { arrth[i].start(); } }
			if( !envioAsincrono ) {
				for( int i=0; i<n; i++ ) { if( arrth[i] != null ) { try { arrth[i].join(30000l); } catch (InterruptedException e) {} } }	
			}
		}
	}

	private abstract class ClonRunnable implements Runnable {
		final String urlServicio;

		ClonRunnable(String url) { this.urlServicio = url; }

		String getUrl() { return this.urlServicio; }

		abstract ClonRunnable clonar(String url);
	}

	private class DeleteRunnable extends ClonRunnable {

		DeleteRunnable(String url) { super(url); }

		E e;
		@Override
		public void run() {
			Logger trace = getTrace();
			String urlServicio = getUrl();
			try {
				if( trace != null ) {
					trace.trace(urlServicio + " DeleteRunnable " + e);
				}
				getDirectProxyFactory(urlServicio).receiveDelete(idCache, thisClassCacheName, e);
			}catch (Throwable th) {
				if( trace != null ) {
					trace.error(urlServicio + " DeleteRunnable " + e, th);
				}
			}
		}

		@Override
		DeleteRunnable clonar(String url) {
			DeleteRunnable dr = new DeleteRunnable(url);
			dr.e = this.e;
			return dr;
		}
	}

	private class UpdateRunnable extends ClonRunnable {

		UpdateRunnable(String url) { super(url); }

		E previo, nuevo;
		@Override
		public void run() {
			Logger trace = getTrace();
			String urlServicio = getUrl();
			try {
				if( trace != null ) {
					trace.trace(urlServicio + " UpdateRunnable " + nuevo);
				}
				getDirectProxyFactory(urlServicio).receiveUpdate(idCache, thisClassCacheName, previo, nuevo);
			}catch (Throwable th) {
				if( trace != null ) {
					trace.error(urlServicio + " UpdateRunnable " + nuevo, th);
				}
			}
		}

		@Override
		UpdateRunnable clonar(String url) {
			UpdateRunnable dr = new UpdateRunnable(url);
			dr.previo = this.previo;
			dr.nuevo = this.nuevo;
			return dr;
		}
	}

	private class NuevoRunnable extends ClonRunnable {

		NuevoRunnable(String url) { super(url); }

		E e;
		@Override
		public void run() {
			Logger trace = getTrace();
			String urlServicio = getUrl();
			try {
				if( trace != null ) {
					trace.trace(urlServicio + " NuevoRunnable " + e);
				}
				getDirectProxyFactory(urlServicio).receiveNuevo(idCache, thisClassCacheName, e);
			}catch (Throwable th) {
				if( trace != null ) {
					trace.error(urlServicio + " NuevoRunnable " + e, th);
				}
			}
		}

		@Override
		NuevoRunnable clonar(String url) {
			NuevoRunnable dr = new NuevoRunnable(url);
			dr.e = this.e;
			return dr;
		}
	}

	public final void receiveDelete(Object e) {
		Logger trace = getTrace();
		if( trace != null ) {
			trace.trace("receiveDelete " + e);
		}
		noTriggerRemove(parseElement(e));
		triggerReceivedDelete(parseElement(e));
		if( balanceada.get() ) {
			strongTriggerDeleteInterno(parseElement(e), false);
		}
	}

	public final void receiveUpdate(Object previo, Object nuevo) {
		Logger trace = getTrace();
		if( trace != null ) {
			trace.trace("receiveUpdate " + nuevo);
		}
		noTriggerPut(parseElement(nuevo));
		triggerReceivedUpdate(parseElement(previo), parseElement(nuevo));
		if( balanceada.get() ) {
			strongTriggerUpdateInterno(parseElement(previo), parseElement(nuevo));	
		}
	}

	public final void receiveNuevo(Object e) {
		Logger trace = getTrace();
		if( trace != null ) {
			trace.trace("receiveNuevo " + e);
		}
		noTriggerPut(parseElement(e));
		triggerReceivedNuevo(parseElement(e));
		if( balanceada.get() ) {
			strongTriggerNuevoInterno(parseElement(e));
		}
	}

	public Object receiveSolicitudRemota(Object e) {
		Logger trace = getTrace();
		if( trace != null ) {
			trace.trace("receiveSolicitudRemota " + e);
		}
		return noTriggerGet(parseElement(e));
	}

	void loadData(BufferDataProvider output) throws IOException {
		Logger trace = getTrace();
		if( trace != null ) {
			trace.trace("loadData");
		}
		Set<E> set = super.keySet();
		output.setSize(set.size());
		for( E e : set ) {
			output.send(e);
		}
	}

	@Override
	public String printCache(boolean byDate) {
		StringBuilder sb = new StringBuilder(super.printCache(byDate));
		String[] parametrosConexiones = copiarConexionesDestino();
		if( parametrosConexiones != null  &&  parametrosConexiones.length > 0 ) {
			sb.append("\n>> Conexiones activas en la nube:\n");
			for( String urlServicio : parametrosConexiones ) {
				sb.append('\t').append(urlServicio).append('\n');
			}
		}
		sb.append("\n>> Conexiones disponibles en la nube:\n");
		sb.append(destinos);
		generateLoadDataTask(1000l);
		return sb.toString();
	}

	@Override
	public void finalize() throws Throwable {
		destinos.clear();
		super.finalize();
	}
}
