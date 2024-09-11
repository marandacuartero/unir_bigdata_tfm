package org.serest4j.http.rest.js;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.serest4j.annotation.service.TMBasicController;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.annotation.service.TMInternal;
import org.serest4j.http.RequestAttributes;
import org.serest4j.http.rest.RestConfigurationLoader;
import org.serest4j.http.rest.RestServicesMapping;

public class JQueryAdapter {

	private Logger logger;

	public JQueryAdapter(Logger logger) {
		if( logger != null  &&  logger.isDebugEnabled() ) {
			this.logger = logger;
		}
	}

	public String build(ClassLoader classLoader, String nombreControlador, String nombreJS, String dominio, RestServicesMapping restServicesMapping) throws Exception {

		Class<?> clase = classLoader.loadClass(nombreControlador);
		boolean esControlador = clase.isInterface();
		if( !esControlador ) {
			Annotation[] anotaciones = clase.getAnnotations();
			if( anotaciones != null ) {
				for( Annotation a : anotaciones ) {
					if( a instanceof TMInjectableContext ) {
						esControlador = true;
					}
					else if( a instanceof TMBasicController ) {
						esControlador = true;
					}
				}
			}
		}
		if( !esControlador ) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if( logger != null ) {
			sb.append("/***************\n");
			sb.append(nombreControlador).append("\n").append(nombreJS).append("\n */").append('\n');
		}
		_build(sb, clase, clase, nombreControlador, nombreJS, dominio, restServicesMapping);
		Class<?> clasePadre = clase.getSuperclass();
		if( clasePadre != null  &&  clasePadre != Object.class ) {
			if( clasePadre.isAnnotationPresent(TMInjectableContext.class)  ||  clasePadre.isAnnotationPresent(TMBasicController.class) ) {
				if( logger != null ) {
					sb.append("\n/***** superclass: " + clasePadre + " *****/\n");
				}
				_build(sb, clasePadre, clase, nombreControlador, nombreJS, dominio, restServicesMapping);
			}
		}
		Class<?>[] interfaces = clase.getInterfaces();
		for( Class<?> interfaz : interfaces ) {
			_build(sb, interfaz, clase, nombreControlador, nombreJS, dominio, restServicesMapping);
		}
		if( logger == null ) {
			int n = sb.length() - 1;
			char prev = ' ';
			for( int i=n; i>=0; i-- ) {
				char c = sb.charAt(i);
				if( c == '\n'  ||  c == '\r'  ||  c == '\t'  ||  c == ' ' ) {
					c = ' ';
					if( prev == ' ' )
						sb.deleteCharAt(i);
					else
						sb.setCharAt(i, c);
				}
				if( c == ','  &&  sb.charAt(i + 1) == ' ' ) {
					sb.deleteCharAt(i + 1);
				}
				prev = c;
			}
		}
		return sb.toString();
	}

	private void _build(StringBuilder sb, Class<?> claseActual, Class<?> claseOrigen, String nombreControlador, String nombreJS, String dominio, RestServicesMapping restServicesMapping) throws Exception {
		TreeMap<String, Object[]> tm = new TreeMap<String, Object[]>();
		Method[] metodos = claseActual.getMethods();
		for( Method m : metodos ) {
			if( m.isAnnotationPresent(TMInternal.class) ) {
				// el acceso esta restringido 
			}
			else if( m.getDeclaringClass().equals(claseActual) ) {
				String nombreFuncion = nombreJS + m.getName();
				String nombreCompleto = m.getDeclaringClass().getName() + "." + m.getName();
				int nargumentos = m.getParameterTypes().length;
				Object[] obj = new Object[] {nombreCompleto, nombreFuncion, String.valueOf(nargumentos), m};
				tm.put(nombreCompleto + "_" + nargumentos, obj);
				RestConfigurationLoader.buildMethodParameters(logger, m, claseOrigen, restServicesMapping);
			}
		}
		for( String key : tm.keySet() ) {
			Object[] obj = tm.get(key);
			String nombreCompleto = obj[0].toString();
			String nombreFuncion = obj[1].toString();
			int nargumentos = Integer.parseInt(obj[2].toString());
			Method metodoActual = (Method)obj[3];
			String mapeoServicio = restServicesMapping.addNextService(null, nombreCompleto);
			if( logger != null )
				logger.trace(mapeoServicio + "=" + nombreCompleto);
			if( mapeoServicio != null ) {
				String contentType = RestConfigurationLoader.buildContentType(logger, mapeoServicio, metodoActual, restServicesMapping);
				if( logger != null ) {
					sb.append("\n/** Mapeando ").append(metodoActual.toGenericString()).append(" en ").append(mapeoServicio).append(' ');
					if( contentType != null ) {
						sb.append("  (ContenType=").append(contentType).append(")");
					}
					sb.append("**/");
				}
				String archivo = buildMetodo(mapeoServicio, nombreFuncion, dominio, nargumentos);
				if( archivo != null ) {
					sb.append('\n');
					sb.append(archivo);
					sb.append('\n');
				}
			}
		}
	}

	private String norm(String pref, int i) {
		String str = "00" + i;
		str = str.substring(str.length() - 2);
		return pref + str;
	}

	private String buildMetodo(String mapeoServicio, String nombre, String dominio, int nargumentos) throws Exception {
		StringBuilder sb1 = new StringBuilder("'").append(dominio).append("'");
		String texto = plantilla.replaceAll("##PETICIONREST##", sb1.toString());
		StringBuilder sb2 = new StringBuilder();
		sb1.setLength(0);
		sb1.append(", data: { ").append(RequestAttributes.FORWARD_NAME).append(" : ").append("##forwardTo##");
		sb1.append(" , ").append(RequestAttributes.FORWARD_TYPE).append(" : ").append("##includeFrom##");
		if( nargumentos > 0 ) {
			for( int i=0; i<=nargumentos; i++ ) {
				if( i == 0 ) {
					String p = norm("p", i);
					sb1.append(", ").append(p).append(" : ").append("'").append(mapeoServicio).append("'");
				}
				else {
					String p = norm("p", i);
					String n = norm("n", i);
					sb1.append(", ").append(p).append(" : ").append(n);
					sb2.append(n).append(", ");
				}
			}
		}
		sb1.append(" } ");
		texto = texto.replaceAll("##DATA##", sb1.toString());
		texto = texto.replaceAll("##ARGUMENTOS##", sb2.toString());
		texto = texto.replaceAll("##METODO##", nombre);
		if( logger == null ) {
			texto = texto.replaceAll("##funcionRespuesta##", "fr");
			texto = texto.replaceAll("##funcionError##", "fe");
			texto = texto.replaceAll("##forwardTo##", "ft");
			texto = texto.replaceAll("##includeFrom##", "fi");
			texto = texto.replaceAll("##dataType##", "dt");
			texto = texto.replaceAll("##RETORNO##", " ");
		}
		else {
			texto = texto.replaceAll("##funcionRespuesta##", "funcionRespuesta");
			texto = texto.replaceAll("##funcionError##", "funcionError");
			texto = texto.replaceAll("##forwardTo##", "forwardTo");
			texto = texto.replaceAll("##includeFrom##", "includeFrom");
			texto = texto.replaceAll("##dataType##", "dataType");
			texto = texto.replaceAll("##RETORNO##", "\n\t");
		}
		return texto;
	}

	private String plantilla = "\n\nfunction ##METODO##(##ARGUMENTOS## ##funcionRespuesta##, ##funcionError##, ##forwardTo##, ##includeFrom##, ##dataType##) { ##RETORNO##"
			+ "$.ajax(##RETORNO##{##RETORNO## type: \"POST\",##RETORNO##url: ##PETICIONREST## ##DATA##,##RETORNO##success: ##funcionRespuesta##,##RETORNO##error: ##funcionError##,##RETORNO##dataType: ##dataType##}##RETORNO##);"
			+ "##RETORNO##}##RETORNO##";
}
