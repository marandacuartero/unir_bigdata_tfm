package org.serest4j.annotation.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Anotacion a nivel de metodo.
 * 
 * Esta anotacion le indica al sistema que no debe de esperar respuesta por parte del controlador.
 * De esta forma, el controlador se queda realizando sus operaciones en background, y el cliente
 * que ejecuta la petición no espera al resultado.
 * 
 * Esta opción es util cuando se quieren invocar operaciones costosas internas del servidor,
 * pero se quieren hacer de forma manual desde el cliente.
 * Por ejemplo para la generación de cierto tipo de informes o estadisticas que requieren
 * costosos accesos a base de datos y fuerte labor de proceso, o para realizar limpiezas
 * de archivos de disco, o copias de seguridad, etc. Labores que pueden invocarse desde
 * un cliente por un usuario de perfil administrador, pero que no requieren que el cliente
 * deba de esperar a la conclusion del proceso en el servidor.
 * 
 * Un metodo con esta anotacion deberia de devolver VOID como unica opción, ya que
 * cualquier otro tipo no será tenido en cuenta.
 * 
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TMNoWaitResponse {

}
