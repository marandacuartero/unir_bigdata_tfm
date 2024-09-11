package org.serest4j.http.rest;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RestServicesMapping {

	private static AtomicInteger ai = new AtomicInteger(0);

	private HashMap<String, String> mapeo = new HashMap<String, String>();

	private final String id;

	RestServicesMapping(String id) {
		this.id = id;
	}

	String getId() {
		return id;
	}

	public String getService(String key) {
		if( key != null ) {
			key = key.trim();
			if( key.length() > 0 ) {
				String value = mapeo.get(key);
				return value;
			}
		}
		return null;
	}

	public void putService(String key, String value) {
		if( key != null ) {
			key = key.trim();
			if( key.length() > 0 ) {
				if( value != null  &&  value.trim().length() > 0 ) {
					mapeo.put(key, value.trim());
				}
			}
		}
	}

	public void removeService(String key) {
		if( key != null ) {
			key = key.trim();
			if( key.length() > 0 ) {
				mapeo.put(key, null);
				mapeo.remove(key);
			}
		}
	}

	public String addNextService(String alias, String value) {
		if( value != null  &&  value.trim().length() > 0 ) {
			if( alias != null  &&  alias.trim().length() > 0 ) {
				String key = alias.trim().toLowerCase();
				if( mapeo.get(key) == null ) {
					mapeo.put(key, value.trim());
					return key;
				}
				return null;
			}
			else {
				int i = ai.addAndGet(1);
				String key = "000000" + i;
				key = key.substring(key.length() - 6);
				key = "srv" + key;
				mapeo.put(key, value.trim());
				return key;
			}
		}
		return null;
	}
}
