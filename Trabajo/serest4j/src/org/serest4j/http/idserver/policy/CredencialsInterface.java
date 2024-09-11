package org.serest4j.http.idserver.policy;

import java.io.Serializable;

import org.apache.log4j.Logger;

/**
 * Metodos comunes a un objeto que representa unas credenciales de acceso de usuario
 * 
 * @author Maranda
 *
 */
public interface CredencialsInterface extends Serializable {

	/**
	 * Dato un nombre, comprueba que para este nombre existen permisos
	 * 
	 * @param metodo El nombre a comprobar
	 * 
	 * @return
	 */
	public boolean comprobarCredenciales(Logger debug, Object metodo);

	/**
	 * Permite comprobar el tipo de estas credenciales antes de aplicarlas
	 * @param metodo
	 * @return
	 */
	public boolean isValid(CredentialsType metodo);
}
