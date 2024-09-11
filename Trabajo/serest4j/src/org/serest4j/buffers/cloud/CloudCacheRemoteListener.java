package org.serest4j.buffers.cloud;

public abstract class CloudCacheRemoteListener<T> {
	
	private final Class<T> proxyName;

	protected CloudCacheRemoteListener(Class<T> proxyName) {
		this.proxyName = proxyName;
	}

	public Class<T> getProxyName() {
		return proxyName;
	}

	public abstract boolean processResponse(T t) throws Throwable;
}
