package org.serest4j.annotation.endpoint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicamos la redireccion del servicio
 * El valor indicado es el identificador del proxy que utilizamos para la redireccion que debera de ser configurado
 * en el archivo de propiedades serest4j/proxy.properties
 * 
 * @author mario.aranda
 *
 */


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface TMRedirectController {
	String value();
}
