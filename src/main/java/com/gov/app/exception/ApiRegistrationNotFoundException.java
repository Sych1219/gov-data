package com.gov.app.exception;

import java.util.UUID;

public class ApiRegistrationNotFoundException extends NotFoundException {

    public ApiRegistrationNotFoundException(UUID apiId) {
        super("No registration found for id " + apiId);
    }
}
