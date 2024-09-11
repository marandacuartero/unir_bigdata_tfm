package org.serest4j.annotation.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.serest4j.db.TransactionalBaseContainer;

/**
 * Anotacion a nivel de objeto.
 * Permite especificar el origen de la base de datos a partir del driver de conexion JDBC
 * 
 * La anotacion admite un atributo obligatorio, que es el prefijo de los parametros
 * usados para constrour la conexion.
 * Por ejemplo para generar una conexion, basada en el prefijo 'es.miaplicacion',
 * la anotacion contendria el prefijo 'es.miaplicacion', y el archivo de parametros contedria los parametros 
 * es.miaplicacion.url, es.miaplicacion.user, es.miaplicacion.pwd, es.miaplicacion.max
 * 
 * @see TransactionalBaseContainer
 * 
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TMDriverParameters {
	String value();
}
