package com.gov.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GovApiRegistrationResponse {

    private UUID id;
    private String name;
    private String status;
    private OffsetDateTime createdAt;

    public GovApiRegistrationResponse(UUID id, String name, String status, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
