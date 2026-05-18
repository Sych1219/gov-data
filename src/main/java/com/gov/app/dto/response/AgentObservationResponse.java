package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AgentObservationResponse {

    private String         id;
    private String         hintId;
    private String         requestId;
    private boolean        hintPresent;
    private int            iterations;
    private boolean        success;
    private OffsetDateTime createdAt;
}
