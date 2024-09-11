package org.serest4j.annotation.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.serest4j.db.TransactionalBaseContainer;

/**
 * 
 * Anotacion a nivel de objeto.
 * Define el origen de la fuente de datos de un objeto BL
 * 
 * En este caso, el origen de la fuente de datos sera un DataSource definido
 * en el servidor de aplicaciones mediante la especificacion JNDI
 * 
 * Este DataSource permite definir conexiones de base de datos expecificas
 * para cada objeto de tipo Bussines Layer o Data Access Layer.
 *
 *
 * @see TransactionalBaseContainer
 *  
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TMDataSource {
	String value();
}
