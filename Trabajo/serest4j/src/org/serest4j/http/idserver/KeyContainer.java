package org.serest4j.http.idserver;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

import org.serest4j.http.idserver.policy.CredencialsInterface;

@SuppressWarnings("serial")
public class KeyContainer implements Serializable {

	private CredencialsInterface[] credencialesUsuario;
	private String contexto;
	private String id;
	private byte[] clave;
	private long ultimoReenvio;
	private Object informacionUsuario;
	private String codigoUsuario;
	private String datosConexion;

	public KeyContainer() {}

	public KeyContainer(String id) {
		setId(id);
	}
	public String getContexto() {
		return contexto;
	}
	public void setContexto(String contexto) {
		this.contexto = contexto;
	}
	public void setDatosConexion(String host, int puerto) {
		this.datosConexion = host + ":" + puerto;
	}
	public void setInformacionUsuario(String codigo, Object informacionUsuario) {
		this.informacionUsuario = informacionUsuario;
		this.codigoUsuario = codigo;
	}
	public Object getInformacionUsuario() {
		return informacionUsuario;
	}
	public String getCodigoUsuario() {
		return codigoUsuario;
	}
	public CredencialsInterface[] getCredencialesUsuario() {
		return credencialesUsuario;
	}
	public void setCredencialesUsuario(CredencialsInterface[] credencialesUsuario) {
		this.credencialesUsuario = credencialesUsuario;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public byte[] getClave() {
		return clave;
	}
	public void setClave(byte[] clave) {
		this.clave = clave;
	}
	public long getUltimoReenvio() {
		return ultimoReenvio;
	}

	public void setUltimoReenvio(long ultimoReenvio) {
		this.ultimoReenvio = ultimoReenvio;
	}

	@Override
	public String toString() {
		return "ContenedorClaves [ id=" + id + ", datosConexion=" + datosConexion + ", contexto=" + contexto 
		        + ", codigoUsuario=" + codigoUsuario
				+ ", ultimoReenvio=" + new Date(ultimoReenvio) + ",\ninformacionUsuario=" + informacionUsuario
				+ ", credencialesUsuario=\n"
				+ Arrays.toString(credencialesUsuario) + "]";
	}
}
