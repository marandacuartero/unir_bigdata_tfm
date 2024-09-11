package org.serest4j.jmx;

public interface CacheEstadisticasMBean {

	public String getCache();

	public String getStrongManager();

	public int getSize();

	public long getTimeout();

	public long getTimeout2();

	public String getTipo();

	public String getServidores();

	public String clearFrom(long l);

	public String listar(boolean byDate);

	public String updateServer(String urlServidor);

	public String removeServer(String urlServidor);
}
