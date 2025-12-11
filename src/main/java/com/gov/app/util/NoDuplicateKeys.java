package com.gov.app.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = NoDuplicateKeysValidator.class)
@Target(FIELD)
@Retention(RUNTIME)
public @interface NoDuplicateKeys {

    String message() default "Duplicate keys detected";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
