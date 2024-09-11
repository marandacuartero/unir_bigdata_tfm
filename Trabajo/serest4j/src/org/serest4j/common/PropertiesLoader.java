package org.serest4j.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.serest4j.cripto.FlowUtility;

import java.util.Base64;

import jakarta.servlet.ServletContext;

public class PropertiesLoader {

	private final AtomicBoolean leido = new AtomicBoolean(false);
	private final Properties properties = new Properties();
	private String nombreCompleto = null;
	private String contexto = null;

	public synchronized Properties loadServletContext(ServletContext context, String nombre, StringBuilder trace) {
		if( leido.compareAndSet(false, true) ) {
			contexto = context.getContextPath();
			nombreCompleto = nombre.toString().trim();
			try {
				Properties p = new Properties();
				if( trace != null ) {
					trace.append("\n*******************************\nCargando Propiedades de " + nombreCompleto);
				}
				try( InputStream inStream = searchInServletContext(context, nombreCompleto, trace) ) {
					p.load(inStream);
				}
				if( trace != null ) {
					list(p, nombreCompleto, trace);
					trace.append("\n*******************************\nFin de Carga de Propiedades de " + nombreCompleto);
				}
				properties.putAll(p);
			} catch (Exception e) {
				if( trace == null ) {
					e.printStackTrace();
				}
				else {
					trace.append("\n*******************************\nERROR en la Carga de Propiedades de " + nombreCompleto + " " + e);
				}
				leido.set(false);
			}
		}
		return properties;
	}

	public String getContexto() {
		return contexto;
	}

	private Collection<String> buildKeys(Properties p) {
		TreeSet<String> ts = new TreeSet<String>();
		Enumeration<Object> e = p.keys();
		while( e.hasMoreElements() ) {
			Object obj = e.nextElement();
			if( obj == null )
				obj = "";
			obj = obj.toString().trim();
			if( obj.toString().length() > 0 )
				ts.add(obj.toString());
		}
		return ts;
	}

	public synchronized List<String> keys() {
		return new ArrayList<String>(buildKeys(properties));
	}

	public synchronized Properties clone() {
		Properties p = new Properties();
		p.putAll(properties);
		return p;
	}

	public synchronized String list() {
		StringBuilder sb = new StringBuilder();
		list(clone(), nombreCompleto == null ? contexto : nombreCompleto, sb);
		return sb.toString();
	}

	private static void list(Properties p, String nombre, StringBuilder sb) {
		sb.append("\n################################\n### Volcado de Propiedades almacenadas en ").append(nombre).append(" ###\n");
		TreeSet<String> ts = new TreeSet<String>();
		for( Object key : p.keySet() ) {
			if( key != null ) {
				ts.add(key.toString());	
			}
		}
		for( String key : ts ) {
			if( key != null ) {
				Object value = p.get(key);
				sb.append('\t');
				sb.append(key);
				sb.append('=');
				sb.append(value);
				sb.append('\n');
			}
		}
		sb.append("\n################################\n### Fin de Volcado de Propiedades almacenadas en ").append(nombre).append(" ###\n");
	}

	private String searchValue(String keyPropiedad, String defaultValue) {
		String value = properties.getProperty(keyPropiedad, defaultValue);
		if( value != null ) {
			if( value.startsWith("ref:") ) {
				return searchValue(value.substring(4), defaultValue);
			}
			else {
				return value;
			}
		}
		else {
			return defaultValue;	
		}
	}

	public synchronized String putProperty(String keyPropiedad, String value) {
		Object retorno = null;
		if( value == null  ||  value.trim().length() <= 0 ) {
			retorno = properties.remove(keyPropiedad);
		}
		else {
			retorno = properties.put(keyPropiedad, value.trim());	
		}
		return retorno == null ? null : retorno.toString();
	}

	public synchronized String getProperty(String keyPropiedad) {
		return getProperty(keyPropiedad, null);
	}

	public synchronized String getProperty(String keyPropiedad, String defaultValue) {
		try {
			return searchValue(keyPropiedad, defaultValue);
		}catch(Exception e){ e.printStackTrace(); }
		return defaultValue;
	}

	public synchronized int getInteger(String keyPropiedad, int defaultValue) {
		try {
			return Integer.parseInt(searchValue(keyPropiedad, Integer.toString(defaultValue)));
		}catch(Exception e){ e.printStackTrace(); }
		return defaultValue;
	}

	public synchronized boolean getBoolean(String keyPropiedad) {
		try {
			return "true".equals(searchValue(keyPropiedad, "false").toLowerCase().trim());
		}catch(Exception e){ e.printStackTrace(); }
		return false;
	}

	public static InputStream searchInServletContext(ServletContext sc, String nombreRecursoOriginal, Logger trace) throws IOException {
		StringBuilder sbTrace = new StringBuilder("searchInServletContext:").append(nombreRecursoOriginal);
		try {
			return searchInServletContext(sc, nombreRecursoOriginal, sbTrace);
		} finally {
			if( trace != null ) {
				trace.trace(sbTrace.toString());
			}
		}
	}

	public static InputStream searchInServletContext(ServletContext sc, String nombreRecursoOriginal, StringBuilder sbTrace) throws IOException {
		StringBuilder sb = new StringBuilder(nombreRecursoOriginal == null ? "" : nombreRecursoOriginal.trim());
		while( sb.length() > 0  &&  sb.charAt(0) == '/' ) {
			sb.deleteCharAt(0);
		}
		if( sb.length() <= 0 ) {
			throw new IOException(nombreRecursoOriginal + " not found...");
		}
		ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
		Object value = null;
		String nombreRecursoSencillo = sb.toString();

		// busqueda de recurso en carpeta predefinida
		try {
			value = sc.getInitParameter("serest4j.properties.prefix");
			if( value != null ) {
				String prefijoBase = value.toString().trim();
				if( prefijoBase.length() > 0 ) {
					sb.insert(0, '/');
					sb.insert(0, prefijoBase);
				}
			}
		} catch (Exception e) {}
		String nombreRecursoCompleto = sb.toString();
		InputStream isret = buscarEnlocal(sc, bout, nombreRecursoCompleto, sbTrace);
		if( isret != null ) {
			return isret;
		}
		// busqueda de recurso en carpeta predefinida

		// busqueda de recurso en classpath estandar
		isret = buscarEnlocal(sc, bout, nombreRecursoSencillo, sbTrace);
		if( isret != null ) {
			return isret;
		}
		// busqueda de recurso en classpath estandar

		// busqueda de recurso en fichero predefinido
		try {
			value = sc.getInitParameter("serest4j.remote.file");
			if( value != null ) {
				File f = new File(value.toString(),  nombreRecursoCompleto);
				if( f.exists()  &&  f.isFile() ) {
					try( InputStream is = new FileInputStream(f) ) {
						FlowUtility.flushStream(is, bout);
						sbTrace.append("\n********* Encontrado ").append(f.getAbsolutePath());
						return new ByteArrayInputStream(bout.toByteArray());
					}
				}
			}
		} catch (Exception e) { }
		// busqueda de recurso en fichero predefinido

		// busqueda de recurso en url remota
		URL url = null;
		try {
			value = sc.getInitParameter("serest4j.remote.url");
			if( value != null ) {
				url = new URI(value.toString() + nombreRecursoCompleto).toURL();
				try( InputStream is = url.openStream() ) {
					FlowUtility.flushStream(is, bout);
					sbTrace.append("\n********* Encontrado ").append(url);
					return new ByteArrayInputStream(bout.toByteArray());
				}
			}
		} catch (Exception e) { }
		// busqueda de recurso en url remota
		throw new IOException(nombreRecursoCompleto + " not found...");
	}

	private static InputStream buscarEnlocal(ServletContext sc, ByteArrayOutputStream bout, String nombreRecursoSencillo, StringBuilder sbTrace) throws IOException {
		String[] names = new String[]{nombreRecursoSencillo, "/" + nombreRecursoSencillo};
		URL url = null;
		for( int i=0; i<names.length; i++ ) {
			String name = new String(names[i]);
			try {
				url = sc.getResource(name);
				if( url != null ) {
					try( InputStream is = sc.getResourceAsStream(name) ) {
						bout.reset();
						FlowUtility.flushStream(is, bout);
						sbTrace.append("\n********* Encontrado ").append(url);
						return new ByteArrayInputStream(bout.toByteArray());
					}
				}
			} catch (Exception e) { }
			url = null;
			try {
				url = sc.getResource("/WEB-INF" + name);
				if( url != null ) {
					try( InputStream is = sc.getResourceAsStream("/WEB-INF" + name) ) {
						bout.reset();
						FlowUtility.flushStream(is, bout);
						sbTrace.append("\n********* Encontrado ").append(url);
						return new ByteArrayInputStream(bout.toByteArray());
					}
				}
			} catch (Exception e) { }
			url = null;
			try {
				url = Thread.currentThread().getContextClassLoader().getResource(name);
				if( url != null ) {
					try( InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name) ) {
						bout.reset();
						FlowUtility.flushStream(is, bout);
						sbTrace.append("\n********* Encontrado ").append(url);
						return new ByteArrayInputStream(bout.toByteArray());
					}
				}
			} catch (Exception e) { }
		}
		return null;
	}

	public static byte[] token2bytes(ServletContext sc, String token, PropertiesLoader pl, Logger trace) {
		byte[] b = null;
		if( token != null  &&  token.length() > 0 ) {
			if( token.startsWith("ref:") ) {
				token = token.substring(4);
				token = pl.getProperty(token, token);
			}
			if( token.startsWith("file:") ) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
				try( InputStream is = PropertiesLoader.searchInServletContext(sc, token.substring(5), trace) ) {
					FlowUtility.flushStream(is, bout);
					b = bout.toByteArray();
				} catch (IOException e) {
					if( trace != null ) { trace.error("init token", e); }
				}
			}
			else if( token.startsWith("f64:") ) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
				try( InputStream is = PropertiesLoader.searchInServletContext(sc, token.substring(4), trace) ) {
					FlowUtility.flushStream(is, bout);
					b = Base64.getDecoder().decode(bout.toString());
				} catch (IOException e) {
					if( trace != null ) { trace.error("init token", e); }
				}
			}
			else if( token.startsWith("b64:") ) {
				try {
					b = Base64.getDecoder().decode(token.substring(4));
				} catch (Exception e) {
					if( trace != null ) { trace.error("init token", e); }
				}
			}
			else {
				try {
					b = token.getBytes("UTF-8");
				}catch(Throwable th){
					if( trace != null ) { trace.error("init token", th); }
				}
			}
			if( b != null  &&  b.length > 0 ) {
				b = key2StrongBytes(b);
			}
		}
		if( b[tam_token-1] == b[tam_token-2] )
			return b;
		else
			return b;
	}

	private static final int tam_token = 2 * 1024;

	public static byte[] key2StrongBytes(byte[] b) {
		if( b.length == 1 ) {
			byte[] _b = new byte[tam_token];
			Arrays.fill(_b, b[0]);
			return _b;
		}
		else {
			int n = tam_token / b.length;
			if( n > 0 ) {
				n++;
				byte[] _b = new byte[b.length * n];
				for( int i=0; i<n; i++ ) {
					System.arraycopy(b, 0, _b, i * b.length, b.length);
				}
				return _b;
			}
			else {
				return b;
			}
		}
	}
}
