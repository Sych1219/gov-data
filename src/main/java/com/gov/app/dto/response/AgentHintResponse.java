package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AgentHintResponse {

    private String         id;
    private String         agent;
    private String         body;
    private String         status;
    private int            seenCount;
    private String         embeddingJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
