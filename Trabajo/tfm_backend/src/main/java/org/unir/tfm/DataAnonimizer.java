package org.unir.tfm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.unir.tfm.dao.ListaNombres;
import org.unir.tfm.dao.Participante;
import org.unir.tfm.dao.Tournament;

public class DataAnonimizer {

	private TreeMap<String, String> tm = new TreeMap<String, String>();
	private ArrayList<String> noUsedNames = new ArrayList<String>();
	private AtomicInteger ai = new AtomicInteger(0);

	void init() {
		tm.clear();
		noUsedNames.clear();
		noUsedNames.addAll(Arrays.asList(ListaNombres.LISTA));
		ai.set(0);
	}

	void refactor(Tournament t, Logger log) {
		if (t.getParticipantes() != null && t.getParticipantes().length > 0) {
			for (Participante p : t.getParticipantes()) {
				StringBuilder sb = new StringBuilder(p.getName().toLowerCase());
				for (int i = sb.length() - 1; i >= 0; i--) {
					char c = sb.charAt(i);
					if (!Character.isAlphabetic(c)) {
						sb.deleteCharAt(i);
					}
				}
				String name = searchNombre(sb.toString());
				log.debug(sb + " > " + name);
				p.setName(name);
			}
		}
	}

	private String searchNombre(String name) {
		if (tm.containsKey(name)) {
			return tm.get(name);
		}
		String suffix = ai.get() <= 0 ? "" : new String("_" + ai.get());
		if (noUsedNames.isEmpty()) {
			noUsedNames.addAll(Arrays.asList(ListaNombres.LISTA));
			int i = ai.incrementAndGet();
			suffix = new String("_" + i);
		}
		int i = noUsedNames.size() == 1 ? 0 : new Random().nextInt(noUsedNames.size());
		String str = noUsedNames.remove(i) + suffix;
		tm.put(name, str);
		return str;
	}
}
