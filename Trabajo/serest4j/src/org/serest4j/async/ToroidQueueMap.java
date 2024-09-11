package org.serest4j.async;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implementacion en modo Mapa de la ColaCliclica
 * 
 * @author Maranda
 *
 * @param <K>
 * @param <V>
 */
public class ToroidQueueMap<K, V> {

	private Map<K, V> valores;
	private ToroidQueue<K> colaCliclica;

	public ToroidQueueMap(Comparator<K> comparator) {
		valores = Collections.synchronizedSortedMap(new TreeMap<K, V>(comparator));
		colaCliclica = new ToroidQueue<K>(false);
	}

	public synchronized void clear() {
		colaCliclica.clear();
		valores.clear();
	}

	public boolean containsKey(Object key) {
		return valores.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return valores.containsValue(value);
	}

	public V get(Object key) {
		return valores.get(key);
	}

	public synchronized V remove(K key) {
		V ret = null;
		if( valores.containsKey(key) )
			ret = valores.put(key, null);
		return ret;
	}

	public boolean isEmpty() {
		return valores.isEmpty();
	}

	public Set<K> keySet() {
		return valores.keySet();
	}

	public synchronized int[] size() {
		return new int[]{colaCliclica.size(), valores.size()};
	}

	public synchronized V mete(K key, V value) {
		if( !valores.containsKey(key) ) {
			colaCliclica.mete(key);
		}
		V ret = valores.put(key, value);
		return ret;
	}

	public synchronized V saca() {
		K key = colaCliclica.saca();
		V ret = null;
		if( key != null ) {
			ret = valores.remove(key);
		}
		return ret;
	}

	@Override
	public String toString() {
		return colaCliclica.toString() + "\n" + valores.toString();
	}
}
