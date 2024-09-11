package org.serest4j.async;

import java.util.Collection;

/**
 * Una cola ciclica es una cola FIFO que no está dimensionada como un array de elementos,
 * sino en base a punteros enlazados. Esto hace que el tamaño de la cola dependa del numero de elementos
 * que se van insertando, y no requiera un incremento sustancial y repentino de un array, como ocurre con
 * el ArrayList comun. Asimismo el sacar un elemento de la lista libera la carga necesaria para desplazar todos los
 * elementos de un vector, operación costosa en vectores de gran tamaño.
 * 
 * La cola ciclica tiene los procesos sincronizados, y esta pensada para
 * que procesos concurrentes vayan poniendo y leyendo datos.  
 * 
 * @author maranda
 *
 * @param <E>
 */

public class ToroidQueue<E>
{
	private int size = 0;
	private int elements = 0;
	private Contenedor cabezalLectura = null;
	private Contenedor cabezalEscritura = null;
	private boolean permiteNulos = false;

	/**
	 * Constructor por defecto. No permite valores nulos.
	 */
	public ToroidQueue() {
		this(false);
	}

	/**
	 * Indica si se permite gestionar valores nulos
	 * 
	 * @param permiteNulos
	 */
	public ToroidQueue(boolean permiteNulos) {
		this.permiteNulos = permiteNulos;
		Contenedor c1 = new Contenedor();
		Contenedor c2 = new Contenedor();
		Contenedor c3 = new Contenedor();

		c1.previo = c3;
		c1.siguiente = c2;

		c2.previo = c1;
		c2.siguiente = c3;

		c3.previo = c2;
		c3.siguiente = c1;

		cabezalLectura = c1;
		cabezalEscritura = c1;
		elements += 3;
	}

	private class Contenedor {
		Contenedor previo = null;
		Contenedor siguiente = null;
		E elemento = null;
		boolean asociado = false;

		@Override
		public String toString() {
			return elemento == null ? "." : elemento.toString();
		}
	}

	public synchronized int size() {
		return size;
	}

	public synchronized int elements() {
		return elements;
	}

	/**
	 * Inserta un elemento nuevo.
	 *  
	 * @param element
	 * @throws NullPointerException cuando no se permiten nulos y el elemento a asociar es nulo
	 */
	public synchronized void mete(E element) {
		if( !permiteNulos ) {
			element.hashCode();
		}
		if( cabezalLectura.hashCode() == cabezalEscritura.hashCode()  &&  cabezalEscritura.asociado )
		{
			// inserto uno
			Contenedor cEp = cabezalEscritura.previo; 
			Contenedor c = new Contenedor();
			cEp.siguiente = c;
			c.previo = cEp;
			cabezalEscritura.previo = c;
			c.siguiente = cabezalEscritura;
			cabezalEscritura = c;
			elements++;
		}
		cabezalEscritura.elemento = element;
		cabezalEscritura.asociado = true;
		cabezalEscritura = cabezalEscritura.siguiente;
		size++;
	}

	public synchronized void metePrimero(E element) {
		int n = size;
		mete(element);
		for( int i=0; i<n; i++ ) {
			E e = saca();
			mete(e);
		}
	}

	public synchronized E sacaUltimo() {
		int n = size - 1;
		for( int i=0; i<n; i++ ) {
			E e = saca();
			mete(e);
		}
		return saca();
	}

	private boolean elementoNulo = false;
	
	public synchronized boolean esElementoNulo() {
		return elementoNulo;
	}

	public synchronized E saca() {
		E ret = null;
		elementoNulo = false;
		if( cabezalLectura.asociado ) {
			ret = cabezalLectura.elemento;
			elementoNulo = ret == null;
			cabezalLectura.elemento = null;
			cabezalLectura.asociado = false;
			cabezalLectura = cabezalLectura.siguiente;
			size--;
		}
		return ret;
	}

	/**
	 * Copia los elementos de este buffer en vector pasado como parametro
	 * @param array
	 */
	public synchronized void copyAll(Collection<E> array) {
		Contenedor destino = cabezalLectura.siguiente;
		while(destino.hashCode() != cabezalLectura.hashCode()) {
			if( destino.elemento != null ) {
				array.add(destino.elemento);
			}
			destino = destino.siguiente;
		}
	}

	public synchronized void clear() {
		E e = saca();
		while( e != null ) {
			e = saca();
		}
	}
	
	public synchronized void trimToSize() {
		ToroidQueue<E> tq = new ToroidQueue<E>(this.permiteNulos);
		E e = saca();
		while( e != null ) {
			tq.mete(e);
			e = saca();
		}
		this.size = tq.size;
		this.elements = tq.elements;
		
		while( this.cabezalLectura.siguiente != null ) {
			this.cabezalLectura = this.cabezalLectura.siguiente;
			this.cabezalLectura.siguiente = null;
			this.cabezalLectura.previo = null;
			this.cabezalLectura.elemento = null;
		}
		while( this.cabezalEscritura.siguiente != null ) {
			this.cabezalEscritura = this.cabezalEscritura.siguiente;
			this.cabezalEscritura.siguiente = null;
			this.cabezalEscritura.previo = null;
			this.cabezalEscritura.elemento = null;
		}
		this.cabezalLectura = tq.cabezalLectura;
		this.cabezalEscritura = tq.cabezalEscritura;
		tq.cabezalLectura = null;
		tq.cabezalEscritura = null;
		tq = null;
	}

	@Override
	public String toString() {
		String str = "[" + cabezalLectura + " ";
		Contenedor destino = cabezalLectura.siguiente;
		while(destino.hashCode() != cabezalLectura.hashCode()) {
			str += destino;
			str += " ";
			destino = destino.siguiente;
		}
		return str.trim() + "] " + size;
	}
}
