package org.unir.tfm.dao;

import java.util.Date;

public class TrazaTorneo {

	private int idTraza;
	private String codigoTorneo;
	private String nombreTorneo;
	private Date fecha;
	private String nombreParticipante;
	private String nombreClub;
	private int puntosEntrada1 = 0;
	private int diezEntrada1 = 0;
	private int xEntrada1 = 0;
	private int puntosEntrada2 = 0;
	private int diezEntrada2 = 0;
	private int xEntrada2 = 0;
	
	public int getIdTraza() {
		return idTraza;
	}
	public void setIdTraza(int idTraza) {
		this.idTraza = idTraza;
	}
	public String getCodigoTorneo() {
		return codigoTorneo;
	}
	public void setCodigoTorneo(String codigoTorneo) {
		this.codigoTorneo = codigoTorneo;
	}
	public String getNombreTorneo() {
		return nombreTorneo;
	}
	public void setNombreTorneo(String nombreTorneo) {
		this.nombreTorneo = nombreTorneo;
	}
	public Date getFecha() {
		return fecha;
	}
	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}
	public String getNombreParticipante() {
		return nombreParticipante;
	}
	public void setNombreParticipante(String nombreParticipante) {
		this.nombreParticipante = nombreParticipante;
	}
	public String getNombreClub() {
		return nombreClub;
	}
	public void setNombreClub(String nombreClub) {
		this.nombreClub = nombreClub;
	}
	public int getPuntosEntrada1() {
		return puntosEntrada1;
	}
	public void setPuntosEntrada1(int puntosEntrada1) {
		this.puntosEntrada1 = puntosEntrada1;
	}
	public int getDiezEntrada1() {
		return diezEntrada1;
	}
	public void setDiezEntrada1(int diezEntrada1) {
		this.diezEntrada1 = diezEntrada1;
	}
	public int getxEntrada1() {
		return xEntrada1;
	}
	public void setxEntrada1(int xEntrada1) {
		this.xEntrada1 = xEntrada1;
	}
	public int getPuntosEntrada2() {
		return puntosEntrada2;
	}
	public void setPuntosEntrada2(int puntosEntrada2) {
		this.puntosEntrada2 = puntosEntrada2;
	}
	public int getDiezEntrada2() {
		return diezEntrada2;
	}
	public void setDiezEntrada2(int diezEntrada2) {
		this.diezEntrada2 = diezEntrada2;
	}
	public int getxEntrada2() {
		return xEntrada2;
	}
	public void setxEntrada2(int xEntrada2) {
		this.xEntrada2 = xEntrada2;
	}
	public int getTotalPuntos() {
		return puntosEntrada1 + puntosEntrada2;
	}
	public int getTotalDieces() {
		return diezEntrada1 + diezEntrada2;
	}
	public int getTotalEquis() {
		return xEntrada1 + xEntrada2;
	}
}
