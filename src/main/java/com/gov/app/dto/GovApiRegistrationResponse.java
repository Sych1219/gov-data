package com.gov.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GovApiRegistrationResponse {

    private UUID id;
    private String name;
    private String status;
    private OffsetDateTime createdAt;
}
