package org.serest4j.buffers.cloud;

import java.util.Comparator;

import org.serest4j.buffers.StrongWeigthCacheManager;

public class GenericCloudCache extends CloudCache<GenericContainer> {

	protected GenericCloudCache(String contexto, String idCache, StrongWeigthCacheManager<GenericContainer> manager) {
		super(contexto, idCache, manager);
	}

	@Override
	protected Comparator<GenericContainer> buildComparator() {
		return new Comparator<GenericContainer>() {

			@Override
			public int compare(GenericContainer o1, GenericContainer o2) {
				return o1.compareTo(o2);
			}
		};
	}
}
