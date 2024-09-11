package org.serest4j.jmx;

public interface ControllerEstadisticasMBean {
	
	public String getServiceName();

	public String getControlador();

	public String getInstancias();

	public boolean isAsincrono();

	public long getCount();

	public long getMin();

	public long getMax();

	public long getMed();

	public String status();

	public void reset();
}
