package org.unir.tfm;

import java.util.ArrayList;

import org.unir.tfm.dao.Calificaciones;
import org.unir.tfm.dao.Participante;
import org.unir.tfm.dao.Tournament;
import org.unir.tfm.dao.TrazaTorneo;

public class BuildTrazas {

	static TrazaTorneo[] convert(Tournament t) {
		ArrayList<TrazaTorneo> al = new ArrayList<TrazaTorneo>();
		for (Participante p : t.getParticipantes()) {
			TrazaTorneo tt = new TrazaTorneo();
			tt.setCodigoTorneo(String.valueOf(t.getId()) + "-" + t.getCode());
			tt.setNombreTorneo(t.getName());
			tt.setFecha(t.getFecha());
			tt.setNombreParticipante(p.getName());
			tt.setNombreClub(p.getClub().getName());
			tt.setIdTraza(p.getId());
			Calificaciones c = p.getCalificaciones();
			tt.setPuntosEntrada1(c.getPuntosEntrada1());
			tt.setDiezEntrada1(c.getDiezEntrada1());
			tt.setxEntrada1(c.getxEntrada1());
			tt.setPuntosEntrada2(c.getPuntosEntrada2());
			tt.setDiezEntrada2(c.getDiezEntrada2());
			tt.setxEntrada2(c.getxEntrada2());
			al.add(tt);
		}
		return al.toArray(new TrazaTorneo[al.size()]);
	}
}
