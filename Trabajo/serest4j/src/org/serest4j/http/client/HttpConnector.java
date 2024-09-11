package org.serest4j.http.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.serest4j.async.BufferDataEof;
import org.serest4j.async.BufferDataInit;
import org.serest4j.async.QueuedBufferDataConsumer;
import org.serest4j.cripto.Clarifier;
import org.serest4j.cripto.NoiseFactory;
import org.serest4j.http.HttpResponseErrorCode;

/**
 * Esta clase establece la conexion con el servidor desde el cliente, y realiza el intercambio de datos.
 * 
 * @author Maranda
 *
 */
public class HttpConnector {

	private final String idClave;
	private final byte[] clave;
	private final ArrayList<String> altaDisponibilidad = new ArrayList<String>();
	private final AtomicInteger contador = new AtomicInteger(0);
	private final AtomicInteger timeoutConexion = new AtomicInteger(0);
	private final AtomicInteger timeoutRespuesta = new AtomicInteger(0);
	private final Logger trace;
	
	public String toString() {
		return "ParametrosConexion: " + altaDisponibilidad;
	}

	public String getUrlServicio() {
		return altaDisponibilidad.get(0);
	}

	public void setTimeout(int tConexion, int tRespuesta) {
		if( tConexion >= 0 )
			timeoutConexion.set(tConexion);
		if( tRespuesta >= 0 )
			timeoutRespuesta.set(tRespuesta);
	}

	/**
	 * Construye el entorno de conexion con la url del proveedor de servicios, y los parametros de encriptacion
	 * 
	 * @param urlServicio
	 * @param clavesEncriptacion {Id de la clave del servidor, byte[] clave de encriptacion binaria}
	 */
	public HttpConnector(String urlServicio, String idClave, byte[] clave, Logger trace) {
		this(new String[]{urlServicio}, idClave, clave, trace);
	}

	public HttpConnector(String[] urlServicio, String idClave, byte[] clave, Logger trace) {
		this.idClave = idClave;
		this.clave = clave;
		if( trace != null  &&  trace.isTraceEnabled() ) {
			this.trace = trace;
		}
		else {
			this.trace = null;
		}
		for( int i=0; i<urlServicio.length; i++ ) {
			this.altaDisponibilidad.add(urlServicio[i]);
		}
		Collections.shuffle(this.altaDisponibilidad);
	}

	/**
	 * 
	 * @param nombreServicio El nombre del servicio a invocar, en la forma nombre.del.servicio.NombreDelControlador.servicioAInvocar
	 * @param argumentos Argumentos necesarios para la invocacion del servicio en el servidor
	 * @return El objeto generado por el servicio
	 * @throws Throwable
	 */
	public synchronized Object procesar(boolean throwsRemoteException, String nombreServicio, Object... argumentos) throws Throwable {
		int n = altaDisponibilidad.size();
		for( int i=0; i<n; i++ ) {
			String urlServicio = altaDisponibilidad.get(i);
			try {
				ProcesadorPeticionesInterno procesadorPeticionesInterno = new ProcesadorPeticionesInterno(this.idClave.toString(), this.clave);
				procesadorPeticionesInterno.urlServicio = urlServicio;
				procesadorPeticionesInterno.nombreServicio = nombreServicio;
				procesadorPeticionesInterno.argumentos = argumentos;
				procesadorPeticionesInterno.throwsRemoteException.compareAndSet(false, throwsRemoteException);

				Object retorno = procesadorPeticionesInterno.procesar();
				if( i != 0 ) {
					Collections.swap(altaDisponibilidad, 0, i);
					contador.set(0);
				}
				if( contador.incrementAndGet() > 10 ) {
					contador.set(0);
					Collections.shuffle(this.altaDisponibilidad);
				}
				return retorno;
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		throw new IOException("La conexion con " + altaDisponibilidad + " no esta activa.");
	}

	private class ProcesadorPeticionesInterno implements Runnable {

		final AtomicReference<HttpURLConnection> httpURLConnectionEnCurso = new AtomicReference<HttpURLConnection>(null);
		final AtomicReference<ObjectInputStream> datosComprimidos = new AtomicReference<ObjectInputStream>(null);
		final AtomicReference<QueuedBufferDataConsumer> cargaDatosIterator = new AtomicReference<QueuedBufferDataConsumer>(null);
		final String idClaveEncriptacion;
		final byte[] claveEncriptacion;

		String urlServicio = null;
		String nombreServicio = null;
		AtomicBoolean throwsRemoteException = new AtomicBoolean(false);
		Object[] argumentos = null;

		ProcesadorPeticionesInterno(String idClaveEncriptacion, byte[] claveEncriptacion) {
			this.idClaveEncriptacion = idClaveEncriptacion;
			this.claveEncriptacion = claveEncriptacion;
		}

		Throwable relanzar(Throwable th) throws Throwable {
			if( throwsRemoteException.get() )
				throw th;
			else
				return th;
		}

		Object procesar() throws Throwable {
			URL conexion = new URI(urlServicio).toURL();
			HttpURLConnection httpURLConnection = null;
			ObjectInputStream objectInputStream = null;
			try {
				httpURLConnection = (HttpURLConnection) conexion.openConnection();
				// configuracion de la conexion
				httpURLConnection.setRequestMethod("POST");
				httpURLConnection.setDoOutput(true);
				httpURLConnection.setDoInput(true);
				int timeout = timeoutConexion.get();
				if( timeout > 100 ) {
					httpURLConnection.setConnectTimeout(timeout);	
				}
				timeout = timeoutRespuesta.get();
				if( timeout > 100 ) {
					httpURLConnection.setReadTimeout(timeout);	
				}
				if( trace != null ) {
					trace.trace("Conectando con " + httpURLConnection + " [" + httpURLConnection.getConnectTimeout() + ", " + httpURLConnection.getReadTimeout() + "] ");
				}
				httpURLConnection.connect();
				if( trace != null ) {
					trace.trace("Conectado con " + httpURLConnection + " !!");
				}
				int ndatos = argumentos == null ? 0 : argumentos.length;
				Object[] datos = new Object[ndatos + 1];
				datos[0] = nombreServicio;
				if( ndatos > 0 ) {
					System.arraycopy(argumentos, 0, datos, 1, ndatos);
				}
				String datosBase64 = NoiseFactory.encripta(idClaveEncriptacion, claveEncriptacion, datos, null);
				httpURLConnection.getOutputStream().write(datosBase64.getBytes("UTF-8"));
				httpURLConnection.getOutputStream().flush();
				try { httpURLConnection.getOutputStream().close(); }catch(Exception e){e.printStackTrace();}
				if( trace != null ) {
					trace.trace("Enviados datos a " + httpURLConnection);
				}
				int tipoError = httpURLConnection.getResponseCode();
				if( trace != null ) {
					trace.trace("Recibida respuesta " + tipoError + " de " + httpURLConnection);
				}
				Arrays.fill(datos, null);
				datos = null;
				if( tipoError == HttpResponseErrorCode.SESION_NO_VALIDA ) {
					return relanzar(new SecurityException("Acceso no autorizado procesando " + conexion + "//" + nombreServicio));	
				}
				else if( tipoError == HttpResponseErrorCode.CREDENCIALES_INSUFICIENTES ) {
					return relanzar(new IllegalAccessException("Servicio no encontrado procesando " + conexion + "//" + nombreServicio));
				}
				else if( tipoError != HttpURLConnection.HTTP_OK ) {
					return relanzar(new IOException("Error " + tipoError + " procesando " + conexion + "//" + nombreServicio));
				}
				else {
					objectInputStream = new ObjectInputStream(httpURLConnection.getInputStream());
					byte[][] b = Clarifier.desencripta1(objectInputStream);
					datos = Clarifier.desencripta2(claveEncriptacion, b[1]);
					if( datos != null  &&  datos.length > 0 ) {
						if( datos[0] instanceof Throwable ) {
							return relanzar((Throwable)datos[0]);
						}
						else if( datos[0] instanceof BufferDataEof ) {
							QueuedBufferDataConsumer retorno = new QueuedBufferDataConsumer();
							retorno.close();
							return retorno;
						}
						else if( datos[0] instanceof BufferDataInit ) {
							BufferDataInit bufferDataInit = (BufferDataInit)datos[0];
							QueuedBufferDataConsumer retorno = new QueuedBufferDataConsumer();
							cargaDatosIterator.set(retorno);
							retorno.setSize(bufferDataInit.getSize());
							if( trace != null ) {
								if( retorno.getSize() >= 0 ) {
									trace.trace("Creando buffer... Se esperan " + retorno.getSize() + " datos.");	
								}
								else {
									trace.trace("Creando buffer de un numero indeterminado de datos");
								}
							}
							httpURLConnectionEnCurso.set(httpURLConnection);
							datosComprimidos.set(objectInputStream);
							httpURLConnection = null;
							objectInputStream = null;
							Thread th = new Thread(this);
							th.setPriority(Thread.MIN_PRIORITY);
							th.setDaemon(true);
							th.start();
							return retorno;
						}
						return datos[0];	
					}
					else {
						return null;
					}
				}
			}
			finally {
				clear(httpURLConnection, objectInputStream, null);
			}
		}

		private void clear(HttpURLConnection httpURLConnection, ObjectInputStream ois, QueuedBufferDataConsumer datosIterator) {
			if( datosIterator != null ) {
				try { datosIterator.close(); } catch (IOException e) {}
			}
			if( httpURLConnection != null ) {
				try { httpURLConnection.getInputStream().close(); }catch(Exception e){}
				try { httpURLConnection.disconnect(); }catch(Exception e){}
			}
			if( ois != null ) {
				try { ois.close(); } catch (Exception e) {}
			}
		}

		@Override
		public void run() {
			try {
				_run();
			} catch (Throwable e) {
				if( trace != null ) {
					trace.trace("error leyendo..." + Thread.currentThread(), e);
				}
				else {
					e.printStackTrace();	
				}
			}
			clear(httpURLConnectionEnCurso.getAndSet(null), datosComprimidos.getAndSet(null), cargaDatosIterator.getAndSet(null));
		}

		private void _run() throws Throwable {
			boolean seguir = true;
			AtomicInteger aicontador = new AtomicInteger(0);
			if( trace != null ) {
				trace.trace("Cargando buffer..." + Thread.currentThread() + "para " + httpURLConnectionEnCurso.get());
			}
			while( seguir ) {
				byte[][] b = Clarifier.desencripta1(datosComprimidos.get());
				Object[] datos = Clarifier.desencripta2(claveEncriptacion, b[1]);
				if( datos[0] instanceof Throwable ) {
					Throwable th = (Throwable)datos[0];
					throw th;
				}
				else if( datos[0] instanceof BufferDataEof ) {
					seguir = false;
				}
				else if( datos[0] != null ) {
					if( aicontador.incrementAndGet() % 100 == 10  &&  trace != null ) {
						trace.trace("Leyendo buffer..." + Thread.currentThread());
					}
					cargaDatosIterator.get().consume(datos[0]);
				}
				else {
					try {Thread.sleep(10l);}catch(Exception e){}
				}
			}
			if( trace != null ) {
				trace.trace("Cerrando buffer..." + Thread.currentThread());
			}
		}
	}
}