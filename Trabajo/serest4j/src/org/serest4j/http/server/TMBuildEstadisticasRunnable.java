package org.serest4j.http.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.serest4j.jmx.ControllerEstadisticas;

public class TMBuildEstadisticasRunnable implements Runnable {

	private final StringBuffer stringBuffer;
	private final ControllerEstadisticas controllerEstadisticas;
	private final Map<String, Integer> hmEstadisticas;

	TMBuildEstadisticasRunnable(StringBuffer stringBuffer, ControllerEstadisticas controllerEstadisticas) {
		this.stringBuffer = stringBuffer;
		this.controllerEstadisticas = controllerEstadisticas;
		this.hmEstadisticas = Collections.synchronizedMap(new HashMap<String, Integer>());
	}

	public void run() {
		stringBuffer.setLength(0);
		long[] value = controllerEstadisticas.getEstadisticas();
		if( value != null ) {
			stringBuffer.append("##Tiempos:\n");
			stringBuffer.append("Total=").append(value[0]).append('\n');
			stringBuffer.append("T.Min=").append(value[1]).append('\n');
			stringBuffer.append("T.Med=").append(value[3]).append('\n');
			stringBuffer.append("T.Max=").append(value[2]).append('\n');

			stringBuffer.append("##Servicios:\n");
			TreeSet<String> al = new TreeSet<String>(hmEstadisticas.keySet());

			for( String key : al ) {
				Integer ii = hmEstadisticas.get(key);
				if( ii != null ) {
					stringBuffer.append(key).append('=').append(ii.toString()).append('\n');
				}
			}
		}
	}

	void build(long time, String contextoServlet) {
		long[] value = controllerEstadisticas.getEstadisticas();
		if( value == null ) {
			value = new long[5];
			value[0] = 1l; value[4] = value[3] = value[2] = value[1] = time;
		}
		else {
			value[0] = value[0] + 1;
			value[1] = Math.min(value[1], time);
			value[2] = Math.max(value[2], time);
			value[4] = value[4] + time;
			value[3] = value[4] / value[0];
		}
		controllerEstadisticas.setEstadisticas(value);
		Integer ii = hmEstadisticas.get(contextoServlet);
		if( ii == null )
			hmEstadisticas.put(contextoServlet, Integer.valueOf(1));
		else
			hmEstadisticas.put(contextoServlet, Integer.valueOf(ii.intValue() + 1));
	}
}
