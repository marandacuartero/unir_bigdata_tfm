package org.serest4j.buffers;

import java.io.PrintWriter;


public final class EmptyStrongWeigthCacheManager<E> implements StrongWeigthCacheManager<E> {

	@Override
	public void manageDeleteElement(E key) {}

	@Override
	public void manageNuevoElement(E key) {}

	@Override
	public E manageLoadElement(E key) { return null; }

	@Override
	public void manageClearCacheFrom(long timer) {}

	@Override
	public void manageInitCache(long timer) {}

	@Override
	public void printAditionalInfo(PrintWriter pw) {}
}
