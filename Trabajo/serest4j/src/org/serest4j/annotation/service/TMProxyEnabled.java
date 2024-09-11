package org.serest4j.annotation.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.serest4j.common.Version;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TMProxyEnabled {
	String token() default Version.VALUE;
}
