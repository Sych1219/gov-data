package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class GovApiTriggerResponse {
    private final String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final UUID apiId;  // For V1 compatibility
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final UUID endpointId;  // For V2 OpenAPI endpoints
    private final int externalStatus;
    private final OffsetDateTime invokedAt;
    private final String requestId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final JsonNode responseBody;
}
