package com.gov.app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String is a valid ISO-8601 datetime with the SGT offset (+08:00).
 * Null values are accepted (use @NotNull separately if required).
 */
@Documented
@Constraint(validatedBy = Iso8601SgtValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Iso8601Sgt {

    String message() default "must be a valid ISO-8601 datetime in SGT (e.g. 2025-01-15T08:30:00+08:00)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
