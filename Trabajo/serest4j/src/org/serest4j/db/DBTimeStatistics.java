package org.serest4j.db;

import java.util.Date;

/**
 * Permite estudiar los tiempos de acceso a la base de datos.
 * 
 * @author Maranda
 *
 */
public class DBTimeStatistics {

	private static final DBTimeStatistics ESTADISTICAS_BD = new DBTimeStatistics();

	public static void addEstadisticas(long duracion, boolean error) {
		ESTADISTICAS_BD._addEstadisticas(duracion, error);
	}

	public static String getEstadisticas() {
		return ESTADISTICAS_BD._getEstadisticas();
	}

	private long tramoDecenas = 0l;
	private long tramoCentenas = 0l;
	private long tramoMiles = 0l;
	private long tramoResto = 0l;

	private long errores = 0l;
	
	private long total;
	private long contador = 0l;

	private long maximo;
	private long horaMaximo = 0l;
	private long contadorMaximo;

	private static final String M10 = "<!-- del orden de las milesimas de segundo -->";
	private static final String M100 = "<!-- del orden de las centesimas de segundo -->";
	private static final String M1000 = "<!-- del orden de las decimas segundo -->";
	private static final String M10000 = "<!-- del orden de los segundos o mayores -->";

	private synchronized void _addEstadisticas(long duracion, boolean error) {
		if( duracion <= 10l ) {
			tramoDecenas++;
		}
		else if( duracion <= 100l ) {
			tramoCentenas++;
		}
		else if( duracion <= 1000l ) {
			tramoMiles++;
		}
		else {
			tramoResto++;
		}
		if( error ) {
			errores++;
		}
		total += duracion;
		contador++;
		if( horaMaximo <= 0l  ||  duracion > maximo ) {
			maximo = duracion;
			horaMaximo = System.currentTimeMillis();
			contadorMaximo = contador;
		}
	}

	private StringBuffer sb = new StringBuffer();
	private synchronized String _getEstadisticas() {
		sb.setLength(0);
		sb.append("<conexiones><errores>").append(errores).append("</errores>");
		if( contador > 0 ) {
			sb.append("<media_msegs>").append(total/contador).append("</media_msegs>");	
		}
		sb.append("<count>").append(contador).append("</count>");
		sb.append("<maximo value='").append(maximo).append("'><hora>").append(new Date(horaMaximo)).append("</hora>");
		sb.append("<posicion>").append(contadorMaximo).append("</posicion>");
		sb.append("</maximo>").append(M10).append("<minus10>").append(tramoDecenas).append("</minus10>");
		sb.append(M100).append("<minus100>").append(tramoCentenas).append("</minus100>");
		sb.append(M1000).append("<minus1000>").append(tramoMiles).append("</minus1000>");
		sb.append(M10000).append("<plus1000>").append(tramoResto).append("</plus1000>");
		sb.append("</conexiones>");
		return sb.toString();
	}
}
