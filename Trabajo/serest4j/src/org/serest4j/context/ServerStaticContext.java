package org.serest4j.context;

import java.util.HashMap;

import org.serest4j.buffers.cloud.CloudCacheRepository;
import org.serest4j.common.GSonFormatter;
import org.serest4j.common.PropertiesLoader;
import org.serest4j.http.idserver.policy.UserDescriptorInstance;
import org.serest4j.proxy.DirectProxyFactory;

public class ServerStaticContext {

	private static final HashMap<String, ServerStaticContext> instancias = new HashMap<String, ServerStaticContext>();

	public static final void put(String key, ServerStaticContext serverStaticContext) {
		synchronized(instancias) {
			instancias.put(key, serverStaticContext);
		}
	}

	public static final ServerStaticContext get(String key) {
		synchronized(instancias) {
			return instancias.get(key);	
		}
	}

	public static final void remove(String key) {
		ServerStaticContext serverStaticContext = null;
		synchronized(instancias) {
			serverStaticContext = instancias.remove(key);	
		}
		if( serverStaticContext != null ) {
			serverStaticContext.hmProxies.clear();
			serverStaticContext.propertiesLoader = null;
			serverStaticContext.gSonFormat = null;
			serverStaticContext.userDescriptorInstance = null;
			if( serverStaticContext.cloudCacheRepository != null ) {
				serverStaticContext.cloudCacheRepository.stop();
				serverStaticContext.cloudCacheRepository = null;
			}
		}
	}

	private PropertiesLoader propertiesLoader;
	private GSonFormatter gSonFormat = null;
	private UserDescriptorInstance userDescriptorInstance;
	private CloudCacheRepository cloudCacheRepository;
	private HashMap<String, DirectProxyFactory> hmProxies = new HashMap<String, DirectProxyFactory>();

	public String getContexto() {
		return propertiesLoader.getContexto();
	}
	public DirectProxyFactory getDirectProxyFactory(String key) {
		return hmProxies.get(key);
	}
	public void putDirectProxyFactory(String key, DirectProxyFactory value) {
		hmProxies.put(key, value);
	}
	public CloudCacheRepository getCloudCacheRepository() {
		return cloudCacheRepository;
	}
	public void setCloudCacheRepository(CloudCacheRepository cloudCacheRepository) {
		this.cloudCacheRepository = cloudCacheRepository;
	}
	public PropertiesLoader getPropertiesLoader() {
		return propertiesLoader;
	}
	public void setPropertiesLoader(PropertiesLoader propertiesLoader) {
		this.propertiesLoader = propertiesLoader;
	}
	public GSonFormatter getgSonFormat() {
		return gSonFormat;
	}
	public void setgSonFormat(GSonFormatter gSonFormat) {
		this.gSonFormat = gSonFormat;
	}
	public UserDescriptorInstance getUserDescriptorInstance() {
		return userDescriptorInstance;
	}
	public void setUserDescriptorInstance(
			UserDescriptorInstance userDescriptorInstance) {
		this.userDescriptorInstance = userDescriptorInstance;
	}
}
