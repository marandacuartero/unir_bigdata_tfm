package org.unir.tfm;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.serest4j.annotation.db.TMDataSource;
import org.serest4j.annotation.rest.TMRest;
import org.serest4j.annotation.service.TMInjectableContext;
import org.serest4j.annotation.service.TMInternal;
import org.serest4j.context.TMContext;
import org.serest4j.db.AutoQueryBuilder;
import org.serest4j.db.TMTransactionalLogger;
import org.unir.tfm.dao.Calificaciones;
import org.unir.tfm.dao.Club;
import org.unir.tfm.dao.Participante;
import org.unir.tfm.dao.Tournament;

@TMInjectableContext
@TMDataSource("jdbc/Ianseo")
public class DataLoader implements IDataLoader {

	TMTransactionalLogger tl;
	TMContext contexto;
	Logger error;

	@TMRest
	@TMInternal
	public Iterator<Tournament> loadTournamentsFrom(Date fecha) throws IOException {
		ArrayList<Tournament> al = new ArrayList<Tournament>();
		try (AutoQueryBuilder qb = new AutoQueryBuilder("tournament")) {
			qb.appendColumn("ToId, ToCode, ToNameShort, ToWhenFrom");
			qb.columnValue("ToWhenFrom").op(">=").dateValue(fecha);
			ResultSet rs = qb.executeQuery(tl);
			while (rs.next()) {
				Tournament tournament = new Tournament();
				int i = 1;
				tournament.setId(rs.getInt(i++));
				tournament.setCode(rs.getString(i++));
				tournament.setName(rs.getString(i++));
				tournament.setFecha(rs.getDate(i++));
				al.add(tournament);
			}
		} catch (Exception e) {
			error.error("En load tournaments from", e);
		}
		for (Tournament t : al) {
			loadParticipantes(t);
			if( t.getParticipantes() != null  &&  t.getParticipantes().length > 0 ) {
				contexto.sendOutput(t);
			}
		}
		al.clear();
		return null;
	}

	private Club loadClub(int id, int torneo) {
		String code = null;
		try (AutoQueryBuilder qb = new AutoQueryBuilder("countries")) {
			qb.appendColumn("CoCode");
			qb.columnValue("CoId").intValue(id);
			qb.columnValue("CoTournament").intValue(torneo);
			ResultSet rs = qb.executeQuery(tl);
			if (rs.next()) {
				code = rs.getString(1);
			}
		} catch (Exception e) {
			error.error("En loadClub", e);
		}
		try (AutoQueryBuilder qb = new AutoQueryBuilder("countries")) {
			qb.appendColumn("CoCode, CoName").verifyValue(true);
			qb.columnValue("CoCode").charValue(code).setDistinct();
			qb.closeWith("order by 2");
			ResultSet rs = qb.executeQuery(tl);
			if (rs.next()) {
				Club club = new Club();
				int i = 1;
				club.setCode(rs.getString(i++));
				club.setName(rs.getString(i++));
				return club;
			}
		} catch (Exception e) {
			error.error("En loadClub", e);
		}
		return null;
	}

	private void loadParticipantes(Tournament tournament) {
		ArrayList<Participante> al = new ArrayList<Participante>();
		try (AutoQueryBuilder qb = new AutoQueryBuilder("entries")) {
			qb.appendColumn("EnId, EnName, EnFirstName, EnCountry").verifyValue(true);
			qb.columnValue("EnTournament").intValue(tournament.getId());
			qb.closeWith("order by 2");
			ResultSet rs = qb.executeQuery(tl);
			while (rs.next()) {
				int i = 1;
				int id = rs.getInt(i++);
				String str = rs.getString(i++) + " " + rs.getString(i++);
				int ecountry = rs.getInt(i++);
				Club club = loadClub(ecountry, tournament.getId());
				Participante participante = new Participante();
				participante.setId(id);
				participante.setName(str);
				participante.setClub(club);
				al.add(participante);
			}
		} catch (Exception e) {
			error.error("En loadParticipantes", e);
		}
		ArrayList<Participante> al2 = new ArrayList<Participante>();
		for (Participante p : al) {
			loadCalificacion(p);
			if( p.getCalificaciones().getTotal() > 0 ) {
				al2.add(p);
			}
		}
		al.clear();
		tournament.setParticipantes(al2.toArray(new Participante[al2.size()]));
	}

	private void loadCalificacion(Participante p) {
		try (AutoQueryBuilder qb = new AutoQueryBuilder("qualifications")) {
			qb.appendColumn("QuD1Score as puntos, QuD1Xnine as dieces, QuD1Gold as equis");
			qb.appendColumn("QuD2Score as puntos2, QuD2Xnine as dieces2, QuD2Gold as equis2").verifyValue(true);
			qb.columnValue("QuId").intValue(p.getId());
			ResultSet rs = qb.executeQuery(tl);
			if (rs.next()) {
				Calificaciones calificaciones = new Calificaciones();
				calificaciones.setPuntosEntrada1(rs.getInt("puntos"));
				calificaciones.setPuntosEntrada2(rs.getInt("puntos2"));
				int id = rs.getInt("dieces");
				int ix = rs.getInt("equis");
				calificaciones.setDiezEntrada1(Math.max(id, ix));
				calificaciones.setxEntrada1(Math.min(id, ix));
				id = rs.getInt("dieces2");
				ix = rs.getInt("equis2");
				calificaciones.setDiezEntrada2(Math.max(id, ix));
				calificaciones.setxEntrada2(Math.min(id, ix));

				p.setCalificaciones(calificaciones);
			}
		} catch (Exception e) {
			error.error("En loadParticipantes", e);
		}
	}
}
