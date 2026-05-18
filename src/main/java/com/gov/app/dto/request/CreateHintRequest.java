package com.gov.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateHintRequest {

    @NotBlank
    private String agent;

    @NotBlank
    private String body;

    private String embeddingJson;
}
