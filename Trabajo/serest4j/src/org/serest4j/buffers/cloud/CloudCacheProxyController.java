package org.serest4j.buffers.cloud;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.serest4j.annotation.endpoint.TMServlet;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.annotation.service.TMProxyEnabled;
import org.serest4j.context.ServerStaticContext;
import org.serest4j.context.TMContext;

@TMInjectableContext
@TMServlet(pattern="ref:serest4j.cloud.proxy")
@TMProxyEnabled(token="ref:serest4j.cloud.token")
public class CloudCacheProxyController implements CloudCacheProxy {

	TMContext contexto;
	Logger debug;

	public CloudCacheProxyController() {}

	private CloudCache<?> getCache(String name) {
		return ServerStaticContext.get(contexto.getContext().getContextPath()).getCloudCacheRepository().getCloud(name);
	}

	public String ping(String idCacheRemota, String name) {
		if( name != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				return cache.getAndValidateIdCache(idCacheRemota);
			}
		}
		return null;
	}

	public void receiveDelete(String idCache, String name, Object obj) {
		if( idCache != null  &&  name != null  &&  obj != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				if( cache.getAndValidateIdCache(idCache) != null ) {
					cache.receiveDelete(obj);	
				}
			}
		}
	}

	public void receiveUpdate(String idCache, String name, Object previo, Object nuevo) {
		if( idCache != null  &&  name != null  &&  previo != null  &&  nuevo != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				if( cache.getAndValidateIdCache(idCache) != null ) {
					cache.receiveUpdate(previo, nuevo);	
				}
			}
		}
	}

	public void receiveNuevo(String idCache, String name, Object obj) {
		if( idCache != null  &&  name != null  &&  obj != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				if( cache.getAndValidateIdCache(idCache) != null ) {
					cache.receiveNuevo(obj);
				}
			}
		}
	}

	public Object receiveSolicitudRemota(String idCache, String name, Object obj) {
		if( idCache != null  &&  name != null  &&  obj != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				if( cache.getAndValidateIdCache(idCache) != null ) {
					return cache.receiveSolicitudRemota(obj);
				}
			}
		}
		return null;
	}

	public Iterator<?> receiveLoadData(String idCache, String name) {
		if( idCache != null  &&  name != null ) {
			CloudCache<?> cache = getCache(name);
			if( cache != null ) {
				if( cache.getAndValidateIdCache(idCache) != null ) {
					try {
						cache.loadData(contexto.getOutput());
					} catch (IOException e) {
						if( debug != null ) {
							debug.error("receiveLoadData(" + idCache + "," + name + ")", e);
						}
					}
				}
			}
		}
		return null;
	}
}
