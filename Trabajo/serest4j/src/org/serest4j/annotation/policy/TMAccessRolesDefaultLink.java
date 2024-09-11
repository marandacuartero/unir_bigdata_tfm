package org.serest4j.annotation.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TMAccessRolesDefaultLink {
	String value() default "";
	Class<?> controller() default Object.class;
}
