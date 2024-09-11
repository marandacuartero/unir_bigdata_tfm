package org.serest4j.http.idserver;

import java.util.Comparator;

import org.serest4j.buffers.cloud.CloudCache;

public class KeyContainerCloudCache extends CloudCache<KeyContainer> {

	public KeyContainerCloudCache(String contexto, String idCache) {
		super(contexto, idCache, new ArrayKeyContainerCacheManager());
	}

	@Override
	public Comparator<KeyContainer> buildComparator() {

		return new Comparator<KeyContainer>() {
			@Override
			public int compare(KeyContainer o1, KeyContainer o2) {
				if( o1 == null  ) {
					if( o2 == null )
						return 0;
					else
						return -1;
				}
				else if ( o2 == null ) {
					return 1;
				}
				else {
					return o1.getId().compareTo(o2.getId());
				}
			}
		};
	}
}
