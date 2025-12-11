package com.gov.app.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class NoDuplicateKeysValidator implements ConstraintValidator<NoDuplicateKeys, Collection<? extends KeyValueAware>> {

    @Override
    public boolean isValid(Collection<? extends KeyValueAware> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        Set<String> seenKeys = new HashSet<>();
        for (KeyValueAware entry : value) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase();
            if (!seenKeys.add(normalized)) {
                return false;
            }
        }
        return true;
    }
}
