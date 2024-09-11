package org.serest4j.annotation.endpoint;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotacion a nivel de objeto y de metodo
 * Utilizada para establecer los enlaces entre la interfaz de un proxy definido en el cliente
 * y su correspondiente controlador en el servidor.
 * 
 * Cuando la anotacion se encuentra sobre el metodo de un controlador del servidor, se realiza
 * una redireccion del mismo hacia el controlador indicado en el enlace.
 * 
 * Su funcionamiento es el siguiente. Por ejemplo, tomamos el proxy EjemploInterfaz
 *  
 * El valor 'Service+' indica que a EjemploInterfaz debemos añadir la palabra Service a la izquierda del
 * nombre del proxy, de manera que obtenemos ServiceEjemploInterfaz como el nombre del controlador
 * 
 * El valor '+Impl' indica que añadimos la palabra Impl a la derecha del nombre del proxy, de manera
 * que obtenemos EjemploInterfazImpl como el nombre del controlador
 * 
 * El valor 'Texto1>>Texto2' indica que debemos reemplazar el Texto1 del nombre completo del proxy
 * con el Texto2. Por ejemplo '.IPru>>.Pru' aplicado a org.serest4j.ejemplos.IPruebaEjemplo
 * nos devolveria el nombre org.serest4j.ejemplos.PruebaEjemplo como nombre de la clase
 * del controlador
 * 
 * Y por ultimo, podemos introducir el nombre completo del controlador, sin el caracter '+' o la cadena '>>'.
 * El texto 'org.serest4j.ejemplos.PruebaEjemplo' nos indica directamente el nombre de la clase
 * que mapea a este proxy en el servidor.
 * 
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TMLinkController {
	String value() default "";
	Class<?> controller() default Object.class;
}
