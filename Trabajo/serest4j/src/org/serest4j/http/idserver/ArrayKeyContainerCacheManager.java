package org.serest4j.http.idserver;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import org.serest4j.buffers.StrongWeigthCacheManager;

public class ArrayKeyContainerCacheManager implements StrongWeigthCacheManager<KeyContainer> {

	private HashMap<String, KeyContainer> hm = new HashMap<String, KeyContainer>();

	@Override
	public void manageDeleteElement(KeyContainer e) {
		if( e != null  &&  e.getId() != null ) {
			hm.remove(e.getId());
		}
	}

	@Override
	public void manageNuevoElement(KeyContainer e) {
		if( e != null  &&  e.getId() != null ) {
			e.setUltimoReenvio(System.currentTimeMillis());
			hm.put(e.getId(), e);
		}
	}

	@Override
	public KeyContainer manageLoadElement(KeyContainer e) {
		if( e != null  &&  e.getId() != null ) {
			e = hm.get(e.getId());
			if( e != null ) {
				e.setUltimoReenvio(System.currentTimeMillis());
				return e;
			}
		}
		return null;
	}

	@Override
	public void manageClearCacheFrom(long timer) {
		ArrayList<KeyContainer> al = new ArrayList<KeyContainer>(hm.values());
		for( KeyContainer e : al ) {
			if( e.getUltimoReenvio() < timer ) {
				hm.remove(e.getId());
			}
		}
	}

	@Override
	public void manageInitCache(long timer) {
	}

	@Override
	public void printAditionalInfo(PrintWriter pw) {
		ArrayList<KeyContainer> al = new ArrayList<KeyContainer>(hm.values());
		if( al.size() > 0 ) {
			pw.println("Sesiones en reserva:");
			pw.println(al);
		}
	}
}
