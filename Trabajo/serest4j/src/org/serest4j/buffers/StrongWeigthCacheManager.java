package org.serest4j.buffers;

import java.io.PrintWriter;

public interface StrongWeigthCacheManager<E> {

	void manageDeleteElement(E e);
	
	void manageNuevoElement(E e);

	E manageLoadElement(E e);

	void manageClearCacheFrom(long timer);

	void manageInitCache(long timer);

	void printAditionalInfo(PrintWriter pw);
}
