package org.serest4j.http.rest.js;

import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.log4j.Logger;

public class Properties2Js {

	private Logger logger;

	public Properties2Js(Logger logger) {
		this.logger = logger;
	}

	private boolean estaEnParametro(String key, String[] parametros) {
		if( parametros != null  &&  parametros.length > 0 ) {
			for( String parametro : parametros ) {
				if( parametro != null  &&  key.toLowerCase().startsWith(parametro) ) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	public String build(ClassLoader classLoader, String prefijoRecursos, String nombreJS, Locale localeParametro, String[] parametros) {
		Locale locale = null;
		if( localeParametro != null ) {
			for( Locale _lo : Locale.getAvailableLocales() ) {
				if( locale == null ) {
					if( _lo.equals(localeParametro) ) {
						locale = _lo;
					}
				}
			}
		}
		if( locale == null ) {
			locale = Locale.getDefault();
		}
		if( logger != null ) {
			logger.debug("Locale utilizado para " + nombreJS + " >> " + locale);	
		}
		TreeMap<String, String> hm = new TreeMap<String, String>();
		ResourceBundle rb = ResourceBundle.getBundle(prefijoRecursos, locale, classLoader);
		Enumeration<String> enumeration = rb.getKeys();
		while( enumeration.hasMoreElements() ) {
			String key = enumeration.nextElement();
			if( key != null  &&  estaEnParametro(key, parametros) ) {
				Object value = rb.getObject(key);
				if( value != null ) {
					String _value = value.toString().trim();
					if( _value.length() > 0 ) {
						hm.put(key, _value);
					}
				}
			}
		}
		String nombreRecursos = nombreJS;
		nombreRecursos = nombreRecursos.replace('/', '.');
		nombreRecursos = nombreRecursos.replace('.', '_');

		StringBuilder sb = new StringBuilder();
		if( hm.size() > 0 ) {
			if( logger != null ) {
				sb.append("/***************\n");
				sb.append(prefijoRecursos).append("\n");
				sb.append(nombreJS).append('\n');
				sb.append(locale).append("\n */");
				sb.append('\n').append('\n');
			}
			String struuid = UUID.randomUUID().toString().replace('-', 'x').toLowerCase();
			sb.append("function _").append(nombreRecursos).append("( ) {");
			ret(sb); tab(sb, 4);
			sb.append("var base = __").append(struuid).append("(arguments[0]);");
			ret(sb); tab(sb, 4);
			sb.append("for (var i = 1; i < arguments.length; i++) {");
			ret(sb); tab(sb, 8);
			sb.append("var replaced = ").append('"').append('{').append('"').append(" + (i - 1) + ").append('"').append('}').append('"');
			ret(sb); tab(sb, 8);
			sb.append(" base = base.replace(replaced, arguments[i]);");
			ret(sb); tab(sb, 4);
			sb.append("}");
			ret(sb); tab(sb, 4);
			sb.append("return base;");
			ret(sb);
			sb.append("}");
			ret(sb);
			ret(sb);
			sb.append("function __").append(struuid).append("( key ) {");
			ret(sb);
			tab(sb, 4);
			sb.append("if( key == undefined  ||  key == null  ||  key == '' ) { return ''; }");
			ret(sb);
			StringBuilder value = new StringBuilder();
			for( String key : hm.keySet() ) {
				value.setLength(0);
				value.append(hm.get(key));
				for( int index=value.length()-1; index>=0; index-- ) {
					char c = value.charAt(index);
					if( c == '\'' ) {
						value.insert(index, "\\");
					}
					else if( c == '\\' ) {
						value.insert(index, "\\");
					}
				}
				tab(sb, 4);
				sb.append("else if( key=='").append(key).append("') {");
				ret(sb);
				tab(sb, 6);
				sb.append("return '").append(value).append("';");
				ret(sb);
				tab(sb, 4);
				sb.append('}');
				ret(sb);
			}
			tab(sb, 4);
			sb.append("else { return key; }");
			ret(sb);
			sb.append('}');
			ret(sb);
		}
		return sb.toString();
	}

	private void ret(StringBuilder sb) {
		if( logger != null ) {
			sb.append('\n');
		}
	}

	private void tab(StringBuilder sb, int level) {
		if( logger != null ) {
			while( level > 0 ) {
				sb.append(' ').append(' ');
				level--;
			}
		}
	}
}
