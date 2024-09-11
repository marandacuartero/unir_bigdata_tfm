package org.serest4j.http.idserver.policy;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Metodos que debe de implementar un descriptor que permite acceder de forma controlada a la informacion de una sesion de usuario
 * 
 * @author Maranda
 *
 */
public interface UserDescriptor {

	/**
	 * Obtiene el codigo de usuario, o su id, o su token que lo representa, a partir de la sesion de usuario
	 * 
	 * @param usuario Este es el objeto que almacena la informaci�n referente a la sesion de usuario
	 * 
	 * @return
	 */
	public String getUserCode(Object usuario);

	public Object[] searchUserRoles(HttpServletRequest request, Object usuario);
}
