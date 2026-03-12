package com.gov.app.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class Iso8601SgtValidator implements ConstraintValidator<Iso8601Sgt, String> {

    private static final ZoneOffset SGT = ZoneOffset.of("+08:00");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            OffsetDateTime dt = OffsetDateTime.parse(value.replace(" ", "+"));
            return SGT.equals(dt.getOffset());
        } catch (Exception e) {
            return false;
        }
    }
}
