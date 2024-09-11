package org.serest4j.annotation.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Connection;

import org.serest4j.context.TMContext;

/**
 * Anotacion a nivel de objeto.
 * Esta anotacion en un controlador de tipo BL o DAL, le indica al sistema que debe de inyectar contexto a este controlador
 * 
 * Para ello el controlador debera definir alguna variable interna de tipo ProxyContexto
 * Si ademas queremos acceso transaccional a base de datos, necesitaremos generar alguna variable del tipo TransaccionLog o Connection
 * 
 * @see TMContext
 * @see Connection 
 * 
 * @author Maranda
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TMInjectableContext {
}
