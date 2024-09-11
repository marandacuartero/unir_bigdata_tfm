package org.serest4j.buffers.cloud;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.serest4j.buffers.LightWeightCache;
import org.serest4j.common.FileLogger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.context.ServerStaticContext;
import org.serest4j.context.TMContext;

import jakarta.servlet.ServletContext;

public class CloudCacheRepository {

	private static CloudCacheRepository _gccr(String contexto) {
		return ServerStaticContext.get(contexto).getCloudCacheRepository();
	}

	static boolean validar(String contexto, String idCache) {
		if( idCache != null ) {
			CloudCacheRepository cloudCacheRepository = _gccr(contexto);
			if( cloudCacheRepository != null ) {
				return cloudCacheRepository.validIdCaches.contains(idCache);
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static <E, T extends LightWeightCache<E>> T get(TMContext contexto, Class<T> name) {
		return (T) _gccr(contexto.getContext().getContextPath()).get(name.getName());	
	}

	
	@SuppressWarnings("unchecked")
	public static <E, T extends LightWeightCache<E>> T get(String contexto, Class<T> name) {
		return (T) _gccr(contexto).get(name.getName());	
	}

	public static <E, T extends LightWeightCache<E>> void put(String contexto, T cache) {
		if( cache != null ) {
			String name = cache.getClass().getName();
			CloudCacheRepository cloudCacheRepository = _gccr(contexto);
			LightWeightCache<?> cacheEnRepositorio = cloudCacheRepository.get(name);
			if( cacheEnRepositorio == null  &&  cache != null ) {
				cloudCacheRepository.caches.put(name, cache);
			}
		}
	}

	public static Timer timer(String contexto) {
		return _gccr(contexto).TIMER_PROCESSOR.get();
	}
	
	private final SortedMap<String, LightWeightCache<?>> caches = Collections.synchronizedSortedMap(new TreeMap<String, LightWeightCache<?>>());
	private final SortedSet<String> validIdCaches = Collections.synchronizedSortedSet(new TreeSet<String>());
	private final AtomicReference<String> inicializado = new AtomicReference<String>(null);
	private final AtomicReference<Timer> TIMER_PROCESSOR = new AtomicReference<Timer>(null);

	synchronized void start(Runnable r) {
		Thread th = new Thread(r);
		th.setDaemon(true);
		th.start();
	}

	public synchronized void stop() {
		for( LightWeightCache<?> lwc : caches.values() ) {
			try { lwc.finalize(); } catch (Throwable e) {}
		}
		caches.clear();
		validIdCaches.clear();
		Timer timer = TIMER_PROCESSOR.getAndSet(null);
		if( timer != null ) {
			timer.cancel();
			if( timer.purge() > 1000 ) {
				System.gc();	
			}
		}
	}

	public boolean init(ServletContext context, Class<?> ssonName) {
		if( inicializado.compareAndSet(null, context.getContextPath()) ) {
			TIMER_PROCESSOR.set(new Timer(context.getContextPath(), true));
			Logger logger = FileLogger.getLogger(context.getContextPath());
			ServerStaticContext.get(context.getContextPath()).setCloudCacheRepository(this);
			try {
				_init(context, ssonName, logger);
				return true;
			} catch (Throwable e) {
				logger.error("Error cargando esta cache", e);
			}
		}
		return false;
	}

	private void _init(ServletContext context, Class<?> ssonName, Logger logger) throws Throwable {
		try( InputStream is = PropertiesLoader.searchInServletContext(context, "cloud.xml", logger) ) {
			SAXBuilder sb = new SAXBuilder();
			Document d = sb.build(is);
			Element root = d.getRootElement();
			List<?> clases = root.getChildren();
			for( Object obj : clases ) {
				Element elemento = (Element)obj;
				String nombreClase = null;
				if( "sson-info".equalsIgnoreCase(elemento.getName()) ) {
					try {
						nombreClase = ssonName.getName();
						logger.debug("Cargando " + nombreClase + "... ");
						_init(context, nombreClase, (Element)elemento, logger);
					}catch (Throwable th) {
						logger.error("Error cargando esta cache", th);
					}
				}
				else if( "class-info".equalsIgnoreCase(elemento.getName()) ) {
					try {
						nombreClase = elemento.getChild("class-name").getText().trim();
						if( !ssonName.getName().equalsIgnoreCase(nombreClase) ) {
							logger.debug("Cargando " + nombreClase + "... ");
							_init(context, nombreClase, (Element)elemento, logger);
						}
					}catch (Throwable th) {
						logger.error("Error cargando esta cache", th);
					}
				}
			}
		}
	}

	private void _init(ServletContext context, String nombreClase, Element datosClase, Logger logger) throws Throwable {
		long timeout = 0l;
		try {
			timeout = Long.parseLong(datosClase.getAttributeValue("timeout"));
			timeout *= 1000l;
		}catch (Exception e) {
			logger.warn("Timer no valido en " + nombreClase + ", se toma timeout=0" + " >> " + e);
		}
		long timeout2 = 0l;
		try {
			timeout2 = Long.parseLong(datosClase.getAttributeValue("timeout2"));
			timeout2 *= 1000l;
		}catch (Exception e) {
			timeout2 = timeout;
			logger.warn("Timer2 no valido en " + nombreClase + ", se toma timer2=" + timeout2 + " >> " + e);
		}
		long timeout3 = 0l;
		try {
			timeout3 = Long.parseLong(datosClase.getAttributeValue("timeout3"));
			timeout3 *= 1000l;
		}catch (Exception e) {
			logger.warn("Timer3 no valido en " + nombreClase + ", se toma timer3=" + timeout3 + " >> " + e);
		}
		boolean balanceada = false;
		try {
			balanceada = datosClase.getChild("balanceada") != null;
		}catch (Exception e) {}
		String nombreFicheroRedundancia = null;
		boolean asincrona = false;
		try {
			nombreFicheroRedundancia = datosClase.getChild("cloud-descriptor").getText();
			try {
				asincrona = datosClase.getChild("asincrona") != null;
			}catch (Exception e) {}
		}catch (Exception e) {
			logger.warn("Descriptor no valido en " + nombreClase + ", la cache solo funcionara en local", e);
		}
		CloudCache<?> instanciaCache = getCloud(nombreClase);
		if( instanciaCache == null ) {
			Class<?> claseCache = context.getClassLoader().loadClass(nombreClase);
			logger.debug("Cargado " + claseCache);
			logger.debug("Descriptor: " + nombreFicheroRedundancia);
			logger.debug("Timer1: " + timeout);
			logger.debug("Timer2: " + timeout2);
			logger.debug("Timer3: " + timeout3);
			String idCache = UUID.randomUUID().toString();
			validIdCaches.add(idCache); // valido el id de esta cache
			Object objInstanciaCache = claseCache.getConstructor(String.class, String.class).newInstance(inicializado.get(), idCache);
			instanciaCache = (CloudCache<?>)objInstanciaCache;
			caches.put(nombreClase, instanciaCache);
			instanciaCache.initCache(TIMER_PROCESSOR.get(), timeout, timeout2, timeout3);
			instanciaCache.initCloudCache(context, nombreFicheroRedundancia, balanceada, asincrona);
		}
	}

	List<String> listNames() {
		return new ArrayList<String>(caches.keySet());
	}

	LightWeightCache<?> get(String name) {
		return caches.get(name);
	}

	CloudCache<?> getCloud(String name) {
		LightWeightCache<?> cache = caches.get(name);
		if( cache != null  &&  cache instanceof CloudCache ) {
			return (CloudCache<?>)cache; 
		}
		return null;
	}
}
