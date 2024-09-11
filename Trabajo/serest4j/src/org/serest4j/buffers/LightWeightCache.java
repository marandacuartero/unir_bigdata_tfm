package org.serest4j.buffers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.serest4j.common.FileLogger;
import org.serest4j.jmx.CacheEstadisticas;
import org.serest4j.jmx.ControllerRegister;

/**
 * Cache para almacenamiento temporal de datos, asociados a una sesi�n.
 * Estas sesiones tienen un tiempo de vida, pasado el cual, si no han sido
 * accedidas son eliminadas de la cache.
 * 
 * @author Maranda
 *
 * @param <E>
 */
public abstract class LightWeightCache<E> {

	private final AtomicReference<Timer> tareaLimpiezaBuffers = new AtomicReference<Timer>(null);
	private final int AIMC = 1000;

	private final TreeMap<E, LongClassComparator> buffer;
	private final TreeMap<LongClassComparator, E> caducidad;
	private final Comparator<E> comparator;
	private final AtomicInteger contador = new AtomicInteger(AIMC);
	private final AtomicLong tiempoEspera = new AtomicLong(0l);
	private final Random random = new Random();
	private final StrongWeigthCacheManager<E> manager;
	private long tiempoCaducidad;
	private long lastClear = System.currentTimeMillis();
	private long strongTimer;
	private CacheLogger trace;
	private final AtomicReference<CacheEstadisticas> arCacheEstadisticas = new AtomicReference<CacheEstadisticas>(null);

	/**
	 * Constructor de este buffer
	 * @param tc el tiempo de vida de cualquiera de las sesiones de este buffer
	 *            si pasado ese tiempo de vida, la sesion no ha sido accedida de ninguna manera, la sesion es borrada
	 *            de la cache
	 */
	protected LightWeightCache(StrongWeigthCacheManager<E> manager) {
		this.caducidad = new TreeMap<LongClassComparator, E>();
		this.comparator = buildComparator();
		this.buffer = new TreeMap<E, LongClassComparator>(comparator);
		this.manager = manager == null ? new EmptyStrongWeigthCacheManager<E>() : manager;
	}

	public final void initCache(Timer timer, long tiempoCaducidad) {
		initCache(timer, tiempoCaducidad, 0l, 0l);
	}

	public final void initCache(Timer timer, long tiempoCaducidad, long strongTimer, long initFromTimer) {
		if( tareaLimpiezaBuffers.compareAndSet(null, timer) ) {
			this.tiempoCaducidad = tiempoCaducidad;
			if( tiempoCaducidad < 0l )
				throw new IllegalArgumentException("tiempoCaducidad = " + tiempoCaducidad);
			this.strongTimer = strongTimer;

			initJMX();
			getCacheJMX().setTimeout(tiempoCaducidad / 1000l);
			getCacheJMX().setTimeout2(strongTimer / 1000l);
			getCacheJMX().setLoadDelay(initFromTimer / 1000l);

			InitTask initTask = new InitTask();
			initTask.l = System.currentTimeMillis() - initFromTimer;
			schedule(initTask, 1000l, 0l);
		}
	}

	protected final CacheEstadisticas getCacheJMX() {
		CacheEstadisticas cacheEstadisticas = arCacheEstadisticas.get();
		if( cacheEstadisticas == null ) {
			cacheEstadisticas = new CacheEstadisticas();
			arCacheEstadisticas.set(cacheEstadisticas);
			cacheEstadisticas.setCache(getClass().getName());
			cacheEstadisticas.setServidores("");
			cacheEstadisticas.setSize(0);
			cacheEstadisticas.setTipo("Local");
			cacheEstadisticas.setStrongManager(this.manager == null ? "" : this.manager.getClass().getName());
		}
		return cacheEstadisticas;
	}

	private class CacheEstadisticasRunnable implements Runnable {

		private final StringBuffer resultado;
		private final StringBuilder metodo;
		private final List<String> argumentos;

		private CacheEstadisticasRunnable(StringBuffer sb, StringBuilder metodo, List<String> argumentos) {
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
		}
	}

	protected void initJMX() {
		CacheEstadisticas cacheEstadisticas = getCacheJMX();
		if( cacheEstadisticas != null ) {
			if( ControllerRegister.registrar("org.serest4j:type=Cache/Local,name=" + getClass().getName(), cacheEstadisticas, FileLogger.getLogger(), null) ) {
				StringBuffer resultado = new StringBuffer();
				StringBuilder metodo = new StringBuilder();
				List<String> argumentos = Collections.synchronizedList(new ArrayList<String>());
				CacheEstadisticasRunnable cacheEstadisticasRunnable = new CacheEstadisticasRunnable(resultado, metodo, argumentos);
				cacheEstadisticas.setProcessor(cacheEstadisticasRunnable, argumentos, metodo, resultado);
			}
		}
	}

	public CacheLogger getLogger() {
		return trace;
	}

	public void setLogger(CacheLogger logger) {
		this.trace = logger;
	}

	protected void schedule(TimerTask tarea, long tiempoDemora, long frecuencia) {
		if( tareaLimpiezaBuffers.get() != null ) {
			if( contador.decrementAndGet() == 0 ) {
				contador.set(AIMC);
				tareaLimpiezaBuffers.get().purge();
			}
			if( frecuencia <= 0l )
				tareaLimpiezaBuffers.get().schedule(tarea, tiempoDemora);
			else
				tareaLimpiezaBuffers.get().schedule(tarea, tiempoDemora, frecuencia);
		}
	}

	private class InitTask extends TimerTask {
		long l;


		@Override
		public void run() {
			try {
				_run();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		private void _run() throws Throwable {			
			manager.manageInitCache(l);
		}
	}

	protected abstract Comparator<E> buildComparator();

	/**
	 * Se ejecuta cada vez que se elimina una sesion del sistema
	 * @param e
	 */
	protected void triggerDelete(E e, boolean interno){}

	/**
	 * Se ejecuta cada vez que una sesion existente cambia de valor
	 * @param previo
	 * @param nuevo
	 */
	protected void triggerUpdate(E previo, E nuevo){}

	/**
	 * Se ejecuta cada vez que se inserta una nueva sesion
	 * @param e
	 */
	protected void triggerNuevo(E e){}

	protected final void strongTriggerDeleteInterno(E e, boolean interno) {
		manager.manageDeleteElement(e);
		if( interno ) {
			manager.manageNuevoElement(e);
		}
	}

	private final void triggerDeleteInterno(final E e, final boolean interno) {
		triggerDelete(e, interno);
		strongTriggerDeleteInterno(e, interno);
	}

	protected final void strongTriggerUpdateInterno(E previo, E nuevo) {
		manager.manageDeleteElement(previo);
		manager.manageNuevoElement(nuevo);
	}

	private final void triggerUpdateInterno(final E previo, final E nuevo) {
		triggerUpdate(previo, nuevo);
		strongTriggerUpdateInterno(previo, nuevo);
	}

	protected final void strongTriggerNuevoInterno(E e) {
		manager.manageNuevoElement(e);
	}

	private final void triggerNuevoInterno(final E e) {
		triggerNuevo(e);
		strongTriggerNuevoInterno(e);
	}

	/**
	 * Coloca el nuevo elemento en todas las ubicaciones de esta cache
	 * @param key
	 * @return El elemento antiguo, en caso de existir
	 */
	public E put(E key) {
		E e = noTriggerPut(key);
		if( e != null ) {
			if( trace != null ) {
				trace.trace("Reemplazando sesion " + e + " por " + key);
			}
			triggerUpdateInterno(e, key);
		}
		else {
			if( trace != null ) {
				trace.trace("Colocando nueva sesion " + key);
			}
			triggerNuevoInterno(key);
			getCacheJMX().setSize(size());
		}
		return e;
	}

	protected final E noTriggerPut(E key) {
		E e = null;
		synchronized (contador) {
			LongClassComparator nuevo = new LongClassComparator(System.currentTimeMillis(), key);
			LongClassComparator previo = buffer.remove(key);
			buffer.put(key, nuevo);
			if( previo != null ) {
				e = caducidad.remove(previo);
			}
			caducidad.put(nuevo, key);
			if( tiempoCaducidad > 0l  &&  tiempoEspera.compareAndSet(0l, System.currentTimeMillis() + tiempoCaducidad) ) {
				schedule(new ClearTimerTask(), tiempoCaducidad, 0l);
			}
		}
		return e;
	}

	/**
	 * Obtiene el elemento indicado de la cache, buscando en todas las ubicaciones posibles
	 * @param key
	 * @return
	 */
	public E get(E key) {
		E e = null;
		LongClassComparator previo = null;
		synchronized (contador) {
			previo = buffer.remove(key);
			if( previo != null ) {
				e = caducidad.remove(previo);
				if( e != null ) {
					LongClassComparator nuevo = new LongClassComparator(System.currentTimeMillis(), e);
					buffer.put(e, nuevo);
					caducidad.put(nuevo, e);
				}
			}
		}
		if( e == null ) {
			e = manager.manageLoadElement(key);
			if( e != null ) {
				noTriggerPut(e);
			}
		}
		return e;
	}

	/**
	 * Elimina el elemento indicado de todas las ubicaciones de la cache
	 * @param key
	 * @return
	 */
	public E remove(E key) {
		E e = get(key);
		if( e != null ) {
			e = noTriggerRemove(e);
			if( e != null ) {
				if( trace != null ) {
					trace.trace("Eliminando sesion " + e);
				}
				triggerDeleteInterno(e, false);
				getCacheJMX().setSize(size());
			}
		}
		return e;
	}

	protected E noTriggerRemove(E key) {
		E e = null;
		synchronized (contador) {
			LongClassComparator previo = buffer.remove(key);
			if( previo != null ) {
				e = caducidad.remove(previo);
			}
		}
		return e;
	}

	/**
	 * Obtiene un sub listado de los elementos que estan solamente en al memoria volatil de la cache
	 * @param fromKey
	 * @param fromInclusive
	 * @param toKey
	 * @param toInclusive
	 * @return
	 */
	public Set<E> subSet(E fromKey, boolean fromInclusive, E toKey, boolean toInclusive) {
		synchronized (contador) {
			TreeSet<E> ts = new TreeSet<E>(buildComparator());
			ts.addAll(buffer.subMap(fromKey, fromInclusive, toKey, toInclusive).keySet());
			return ts;
		}
	}

	/**
	 * Obtiene un listado de los elementos almacenados en la memoria volatil de la cache
	 * @return
	 */
	public Set<E> keySet() {
		return keySet(buildComparator());
	}

	/**
	 * Obtiene un listado de los elementos almacenados en la memoria volatil de la cache
	 * ordenados segun el comparador indicado
	 * @param comparator
	 * @return
	 */
	public Set<E> keySet(Comparator<E> comparator) {
		synchronized (contador) {
			TreeSet<E> ts = new TreeSet<E>(comparator);
			ts.addAll(buffer.keySet());
			return ts;
		}
	}

	/**
	 * Comprueba si existe el elemento en la memoria volatil de la cache  
	 * @param key
	 * @return
	 */
	public boolean contains(E key) {
		synchronized (contador) {
			LongClassComparator previo = buffer.get(key);
			if( previo != null ) {
				return caducidad.get(previo) != null;
			}
		}
		return false;
	}

	/**
	 * Comprueba si se ha vaciado la memoria volatil de la cache
	 * @return
	 */
	public boolean isEmpty() {
		synchronized (contador) {
			return buffer.isEmpty()  ||  caducidad.isEmpty();
		}
	}

	/**
	 * Obtiene el numero de elementos de la memoria volatil de la cache
	 * @return
	 */
	public int size() {
		synchronized (contador) {
			return Math.min(buffer.size(), caducidad.size());
		}
	}

	/**
	 * Se borran aquellas sesiones cuyo ultimo acceso se realizo anteriormente al valor indicado
	 * @param l
	 * @return
	 */
	public synchronized List<E> clearBufferFrom(long l) {
		synchronized (contador) {
			if( caducidad.isEmpty() ) {
				buffer.clear();
				return Collections.emptyList();
			}
		}
		ArrayList<LongClassComparator> al = new ArrayList<LongClassComparator>();
		synchronized (contador) {
			al.addAll(caducidad.headMap(new LongClassComparator(l, null), true).keySet());	
		}
		List<E> resultados = new ArrayList<E>();
		E e = null;
		for(LongClassComparator key : al ) {
			synchronized (contador) {
				e = caducidad.remove(key);
				if( e != null ) {
					resultados.add(e);
					buffer.remove(e);
				}
			}
			if( e != null ) {
				if( trace != null ) {
					trace.trace("Limpiando sesion caducada " + e);
				}
				triggerDeleteInterno(e, true);
			}
		}
		al.clear();
		al = null;
		getCacheJMX().setSize(size());
		return resultados;
	}

	private void previousClearTimerTask(){
		if( tiempoCaducidad > 0l  &&  strongTimer >= tiempoCaducidad ) {
			long timer = System.currentTimeMillis() - strongTimer;
			System.out.println("LightWeightCache.previousClearTimerTask() from " + new Date(timer));
			manager.manageClearCacheFrom(timer);	
		}
	}

	private class ClearTimerTask extends TimerTask {

		@Override
		public void run() {
			try {
				_run();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		private void _run() throws Throwable {
			if( trace != null ) {
				trace.trace("Ejecutando limpieza de cache... ");
			}
			previousClearTimerTask();
			long l = System.currentTimeMillis();
			lastClear = l;
			l = l - tiempoCaducidad;
			List<E> lista = clearBufferFrom(l);
			if( lista != null ) {
				lista.clear();
			}
			lista = null;
			long espera = tiempoCaducidad;
			if( caducidad.isEmpty() ) {
				tiempoEspera.set(0l);
			} 
			else {
				LongClassComparator siguiente = caducidad.firstKey();
				if( siguiente != null ) {
					espera = siguiente.l - l;
					if( espera <= tiempoCaducidad  &&  tiempoCaducidad <= 60000l )
						espera = tiempoCaducidad + random.nextInt(100);
					else
						espera += 100l;
				}
				tiempoEspera.set(System.currentTimeMillis() + espera);
				schedule(new ClearTimerTask(), espera, 0l);
			}
		}
	}

	private class LongClassComparator implements Comparable<LongClassComparator> {
		final long l;
		final E e;

		LongClassComparator(long l, E e) {
			this.l = l;
			this.e = e;
		}

		@Override
		public String toString() {
			return "[" + new Date(l) + " " + e + "]";
		}

		@Override
		public int compareTo(LongClassComparator o) {
			if( o == null )
				return 1;
			else if( l > o.l )
				return 1;
			else if( l < o.l )
				return -1;
			else if( e == null  &&  o.e == null )
				return 0;
			else if( e != null  &&  o.e == null )
				return 1;
			else if( e == null  &&  o.e != null )
				return -1;
			else
				return comparator.compare(e, o.e);
		}
	}

	public void list(PrintWriter out, boolean byDate) {
		int size = size();
		if( size <= 0 ) {
			if( tiempoCaducidad > 0l ) {
				out.print("Last Use: ");
				out.print(new Date(lastClear));
				out.print(", Empty Buffer");
			}
			else {
				out.print("Empty Buffer");
			}
		}
		else {
			if( tiempoCaducidad > 0l ) {
				out.print("Last Clear: ");
				out.print(new Date(lastClear));
				out.print(", Next Clear: ");
				out.print(new Date(tiempoEspera.get()));
				out.print(", Actual Size=");
				out.print(size);
				out.print('\n');
				out.println();
			}
			if( byDate ) {
				synchronized (contador) {
					for( LongClassComparator l : caducidad.descendingKeySet() ) {
						E cc = caducidad.get(l);
						if( cc != null ) {
							out.print("\n[Last Acces: ");
							out.print(new Date(l.l));
							out.print(", Data: ");
							out.print(cc.toString());
							out.print(']');
							out.print('\n');
						}
					}
				}
			}
			else {
				synchronized (contador) {
					for( E cc : buffer.keySet() ) {
						LongClassComparator l = buffer.get(cc);
						if( l != null ) {
							out.print("\n[Last Acces: ");
							out.print(new Date(l.l));
							out.print(", Data: ");
							out.print(cc.toString());
							out.print(']');
							out.print('\n');
						}
					}
				}
			}
		}
		out.println('\n');
		manager.printAditionalInfo(out);
		getCacheJMX().setSize(size());
	}

	public String printCache(boolean byDate) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		list(pw, byDate);
		return sw.getBuffer().toString();
	}

	@Override
	public void finalize() throws Throwable {
		buffer.clear();
		caducidad.clear();
	}
}
