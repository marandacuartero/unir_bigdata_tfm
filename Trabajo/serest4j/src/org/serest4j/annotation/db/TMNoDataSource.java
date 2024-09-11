package org.serest4j.annotation.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.serest4j.db.TransactionalBaseContainer;

/**
 * 
 * Anotacion a nivel de objeto.
 * Indica que este objeto no debe instanciar ningun pool de conexiones a base de datos
 *
 * @see TransactionalBaseContainer
 *  
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TMNoDataSource {
}
