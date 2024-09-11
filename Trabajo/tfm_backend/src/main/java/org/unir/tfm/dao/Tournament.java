package org.unir.tfm.dao;

import java.util.Date;

public class Tournament {
	
	private int id;
	private String code;
	private String name;
	private Date fecha;
	private Participante[] participantes;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Date getFecha() {
		return fecha;
	}
	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}
	public Participante[] getParticipantes() {
		return participantes;
	}
	public void setParticipantes(Participante[] participantes) {
		this.participantes = participantes;
	}
}
