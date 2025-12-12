package com.gov.app.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class GovApiTriggerRequest {

    private Map<String, Object> query;
    private Map<String, Object> body;
    private Map<String, String> headerOverrides;
    private Boolean useExampleDefaults;

    @AssertTrue(message = "Runtime query payload must not be empty when provided")
    public boolean isQueryValid() {
        return query == null || !query.isEmpty();
    }

    @AssertTrue(message = "Runtime body payload must not be empty when provided")
    public boolean isBodyValid() {
        return body == null || !body.isEmpty();
    }

    @AssertTrue(message = "Header overrides must not be empty when provided")
    public boolean isHeaderOverridesValid() {
        return headerOverrides == null || !headerOverrides.isEmpty();
    }
}
