package org.serest4j.annotation.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Aquellos metodos que lleven este tipo de anotacion,
 * seran auditados por el gestor de auditorias generico
 * Este auditor inserta un registro en la tabla de auditorias que contiene
 * el codigo de usuario, el nombre del controlador y el servicio solicitado,
 * asi como los parametros enviados al servicio y el retorno del mismo.
 * 
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TMAudit {
}
