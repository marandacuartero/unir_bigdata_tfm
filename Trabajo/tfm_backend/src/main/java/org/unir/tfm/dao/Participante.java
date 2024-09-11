package org.unir.tfm.dao;

public class Participante {
	private int id;
	private String name;
	private Club club;
	private Calificaciones calificaciones;

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Club getClub() {
		return club;
	}
	public void setClub(Club club) {
		this.club = club;
	}
	public Calificaciones getCalificaciones() {
		return calificaciones;
	}
	public void setCalificaciones(Calificaciones calificaciones) {
		this.calificaciones = calificaciones;
	}
}
