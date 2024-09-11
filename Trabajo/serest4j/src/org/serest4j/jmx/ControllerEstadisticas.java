package org.serest4j.jmx;

public class ControllerEstadisticas implements ControllerEstadisticasMBean {

	private String serviceName;
	private String controlador;
	private int instancias;
	private long[] estadisticas;
	private Runnable runnable;
	private Runnable estatus;
	private StringBuffer stringBuffer;
	private boolean asincrono;

	public String getControlador() {
		return controlador;
	}

	public void setControlador(String controlador) {
		this.controlador = controlador;
	}

	public boolean isAsincrono() {
		return asincrono;
	}

	public void setAsincrono(boolean asincrono) {
		this.asincrono = asincrono;
	}

	public String getInstancias() {
		return instancias < 0 ? "?" : Integer.toString(instancias);
	}

	public void setInstancias(int instancias) {
		this.instancias = instancias;
	}

	@Override
	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public long[] getEstadisticas() {
		return estadisticas;
	}

	public void setEstadisticas(long[] estadisticas) {
		this.estadisticas = estadisticas;
	}

	public void setEstatus(StringBuffer stringBuffer, Runnable runnable) {
		this.stringBuffer = stringBuffer;
		this.estatus = runnable;
	}

	@Override
	public String status() {
		if( estatus != null  &&  stringBuffer != null ) {
			stringBuffer.setLength(0);
			estatus.run();
			return stringBuffer.toString();
		}
		else if( stringBuffer != null ) {
			return stringBuffer.toString();
		}
		else {
			return "";
		}
	}

	@Override
	public void reset() {
		if( runnable != null ) {
			runnable.run();
		}
		setEstadisticas(null);
		setInstancias(-1);
	}

	@Override
	public long getCount() {
		return estadisticas == null ? 0l : estadisticas[0];
	}

	@Override
	public long getMin() {
		return estadisticas == null ? 0l : estadisticas[1];
	}

	@Override
	public long getMax() {
		return estadisticas == null ? 0l : estadisticas[2];
	}

	@Override
	public long getMed() {
		return estadisticas == null ? 0l : estadisticas[3];
	}

	public void setRunnable(Runnable runnable) {
		this.runnable = runnable;
	}
}
