package org.serest4j.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.context.LinkCalculator;
import org.serest4j.cripto.TokenFactory;
import org.serest4j.http.client.HttpConnector;

/**
 * Realiza invocaciones directas entre servidores por protocolo interno.
 * Utiliza como base de configuracion el filtro de redireccion de peticiones externas
 * 
 * @author maranda
 *
 */
public class DirectProxyFactory {

	public static DirectProxyFactory newInstance(byte[] tokenClave, Logger loggerContexto, String... urlServicio) {
		tokenClave = PropertiesLoader.key2StrongBytes(tokenClave);
		if( urlServicio != null  &&  urlServicio.length > 0 ) {
			DirectProxyFactory directProxyFactory = new DirectProxyFactory(tokenClave, loggerContexto, urlServicio);
			if( directProxyFactory.urlServicio.length > 0 ) {
				return directProxyFactory;
			}
		}
		return null;
	}

	public Object procesarPeticion(String nombreMetodo, Object... argumentos) throws Throwable {
		return getProxy().procesarParaFiltro(nombreMetodo, argumentos);
	}

	@SuppressWarnings("unchecked")
	public <T> T getProxy(Class<T> clase) {
		T newProxyInstance = (T) Proxy.newProxyInstance(clase.getClassLoader(),
				new Class[] { clase },
				getProxy());
		return newProxyInstance;
	}

	public void setTimeout(int tConexion, int tRespuesta) {
		this.timeoutConexion = tConexion;
		this.timeoutRespuesta = tRespuesta;
	}
	
	// Implementacion local
	private String[] urlServicio;
	private String idProxy;
	private byte[] clave;
	private Logger logger;
	private int timeoutConexion = -1;
	private int timeoutRespuesta = -1;


	private DirectProxyFactory(byte[] tokenClave, Logger loggerContexto, String... urlServicio) {
		this.urlServicio = processUrls(urlServicio);
		this.idProxy = TokenFactory.make();
		this.clave = tokenClave;
		this.logger = loggerContexto;
		if( loggerContexto != null )
			loggerContexto.debug("Inicializando DirectProxyFactory, destinos=" + Arrays.toString(urlServicio));
	}

	private DirectConexion getProxy() {
		return new DirectConexion(this.urlServicio, this.idProxy, this.clave, this.timeoutConexion, this.timeoutRespuesta, this.logger);
	}

	private class DirectConexion extends HttpConnector implements InvocationHandler {

		public DirectConexion(String[] urlServicio, String idClave, byte[] clave, int timeoutConexion, int timeoutRespuesta, Logger trace) {
			super(urlServicio, idClave, clave, trace);
			super.setTimeout(timeoutConexion, timeoutRespuesta);
		}

		private Object procesarParaFiltro(String nombreServicio, Object... argumentos) throws Throwable {
			return super.procesar(false, nombreServicio, argumentos);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object retorno = null;
			long l = System.currentTimeMillis();
			try {
				if( method.getDeclaringClass().isInterface() ) {
					String nombreServicio = LinkCalculator.nombreServicioControlador(method);
					retorno = super.procesar(true, nombreServicio, args);
					if( logger != null  &&  logger.isDebugEnabled() ) {
						l = System.currentTimeMillis() - l;
						if( retorno != null  &&  retorno.getClass().isArray() )
							logger.debug(l + " msgs. Redireccionando servicio " + method + " con " + Arrays.deepToString(args) + " devuelve " + Arrays.deepToString((Object[])retorno));
						else
							logger.debug(l + " msgs. Redireccionando servicio " + method + " con " + Arrays.deepToString(args) + " devuelve " + retorno);
					}
				}
			}
			catch( Throwable th) {
				if( logger != null ) {
					logger.error("Redireccionando invocacion " + method + " con " + Arrays.deepToString(args), th);	
				}
				Throwable _th = th;
				while( _th.getCause() != null ) {
					_th = _th.getCause();
				}
				throw _th;
			}
			return retorno;
		}
	}

	private static String[] processUrls(String... arrurls) {
		ArrayList<String> al = new ArrayList<String>();
		for( String url : arrurls ) {
			String value = url.toString().trim();
			if( value.length() > 0 ) {
				for( String value2 : value.replace(',', ';').split(";") ) {
					if( value2.trim().length() > 0 ) {
						al.add(value2.trim());
					}
				}
			}			
		}
		String[] retorno = new String[al.size()];
		retorno[0] = al.get(0);
		return al.toArray(retorno);
	}
}
