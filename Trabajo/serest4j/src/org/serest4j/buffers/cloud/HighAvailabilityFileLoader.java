package org.serest4j.buffers.cloud;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import org.apache.log4j.Logger;
import org.serest4j.common.PropertiesLoader;

import jakarta.servlet.ServletContext;

public class HighAvailabilityFileLoader {

	public static void loadFile(List<String> al, String nombreFichero, ServletContext context, PropertiesLoader pl, Logger trace) {
		al.clear();
		try( InputStream is = PropertiesLoader.searchInServletContext(context, nombreFichero, trace) ) {
			try( InputStreamReader isr = new InputStreamReader(is) ) {
				try( LineNumberReader lr = new LineNumberReader(isr) ) {
					String str = lr.readLine();
						while( str != null ) {
							str = str.trim();
							if( str.length() > 0 ) {
								if( str.startsWith("http://")  ||  str.startsWith("https://") ) {
									al.add(str);
								}
								else if( str.startsWith("ref:") ) {
									str = pl.getProperty(str.substring(4), "").trim();
									if( str.length() > 0 ) {
										if( str.startsWith("http://")  ||  str.startsWith("https://") ) {
											al.add(str);
										}
									}
								}
							}
							str = lr.readLine();
						}
					}
				}
		}
		catch (Exception e) {
			if( trace != null )
				trace.error("CargaAltaDisponibilidad en " + nombreFichero + ": No se ha cargado ninguna configuracion de servidores de claves en alta disponibilidad");
		}
		if( trace != null )
			trace.error("CargaAltaDisponibilidad en " + nombreFichero + ": " + al);
	}
}
