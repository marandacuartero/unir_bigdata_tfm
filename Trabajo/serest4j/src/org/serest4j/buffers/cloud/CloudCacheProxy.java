package org.serest4j.buffers.cloud;

import java.util.Iterator;

import org.serest4j.annotation.endpoint.TMLinkController;

@TMLinkController("org.serest4j.buffers.cloud.CloudCacheProxyController")
public interface CloudCacheProxy {

	public String ping(String idCache, String name);

	public void receiveDelete(String idCache, String name, Object obj);

	public void receiveUpdate(String idCache, String name, Object obj1, Object obj2);

	public void receiveNuevo(String idCache, String name, Object obj);

	public Object receiveSolicitudRemota(String idCache, String name, Object obj);

	public Iterator<?> receiveLoadData(String idCache, String name);
}
