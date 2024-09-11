package org.serest4j.jmx;

import java.util.List;

public class CacheEstadisticas implements CacheEstadisticasMBean {

	private String cache;
	private String strongManager;
	private int size;
	private String servidores;
	private Runnable runnableProcessor;
	private StringBuilder metodo;
	private List<String> argumentos;
	private StringBuffer resultado;
	private long timeout;
	private long timeout2;
	private long loadDelay;
	private String tipo;

	public String getTipo() {
		return tipo;
	}
	public void setTipo(String tipo) {
		this.tipo = tipo;
	}
	public long getTimeout() {
		return timeout;
	}
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	public long getTimeout2() {
		return timeout2;
	}
	public void setTimeout2(long timeout2) {
		this.timeout2 = timeout2;
	}
	public long getLoadDelay() {
		return loadDelay;
	}
	public void setLoadDelay(long loadDelay) {
		this.loadDelay = loadDelay;
	}
	public String getCache() {
		return cache;
	}
	public void setCache(String cache) {
		this.cache = cache;
	}
	public String getStrongManager() {
		return strongManager;
	}
	public void setStrongManager(String strongManager) {
		this.strongManager = strongManager;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	public String getServidores() {
		return servidores;
	}
	public void setServidores(String servidores) {
		this.servidores = servidores;
	}

	public void setProcessor(Runnable r, List<String> l, StringBuilder metodo, StringBuffer resultado) {
		this.runnableProcessor = r;
		this.argumentos = l;
		this.metodo = metodo;
		this.resultado = resultado;
	}

	@Override
	public String clearFrom(long l) {
		argumentos.clear();
		metodo.setLength(0);
		argumentos.add(Long.toString(l));
		metodo.append("clearFrom");
		runnableProcessor.run();
		return resultado.toString(); 
	}

	@Override
	public String listar(boolean byDate) {
		argumentos.clear();
		metodo.setLength(0);
		argumentos.add(byDate ? "true" : "false");
		metodo.append("listar");
		runnableProcessor.run();
		return resultado.toString(); 
	}

	@Override
	public String updateServer(String urlServidor) {
		argumentos.clear();
		metodo.setLength(0);
		argumentos.add(urlServidor);
		metodo.append("updateServer");
		runnableProcessor.run();
		return resultado.toString(); 
	}

	@Override
	public String removeServer(String urlServidor) {
		argumentos.clear();
		metodo.setLength(0);
		argumentos.add(urlServidor);
		metodo.append("removeServer");
		runnableProcessor.run();
		return resultado.toString();
	}
}
