package org.serest4j.db;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.NamingException;

/**
 * Interfaz que establece los requerimientos minimos del pool de conexiones de este sistema
 * 
 * @author Maranda
 *
 */
public interface TransactionalPool {

	/**
	 * 
	 * @return Me sirve la siguiente conexion a base de datos que tenga disponible en ese momento
	 * 
	 * @throws SQLException
	 * @throws NamingException
	 */
	Connection next() throws SQLException, NamingException;

	/**
	 * Devuelve al pool de conexiones la conexion que acabo de utilizar, indicando ademas si
	 * existio algun error durante el proceso transaccional, lo que permitira al sistema
	 * ejecutar un commit o un rollback llegado el caso
	 * 
	 * @param c
	 * @param error
	 */
	void set(Connection c, boolean error);

	/**
	 * Devuelve el identificador de este pool de conexiones
	 * @return
	 */
	String getId();
}
