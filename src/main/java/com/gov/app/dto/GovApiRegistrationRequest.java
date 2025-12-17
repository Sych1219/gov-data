package com.gov.app.dto;

import com.gov.app.util.HttpsUrl;
import com.gov.app.util.KeyValueAware;
import com.gov.app.util.NoDuplicateKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GovApiRegistrationRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "baseUrl is required")
    @HttpsUrl
    private String baseUrl;

    @NotNull(message = "httpMethod is required")
    private GovHttpMethod httpMethod;

    @Valid
    @NoDuplicateKeys(message = "Duplicate header keys are not allowed")
    private List<ApiHeader> headers;

    @Valid
    @NoDuplicateKeys(message = "Duplicate query parameter keys are not allowed")
    private List<ApiQueryParam> queryParams;

    @Valid
    @NoDuplicateKeys(message = "Duplicate body parameter keys are not allowed")
    private List<ApiBodyParam> bodyParams;

    @Size(min = 3, message = "description must be at least 3 characters")
    private String description;

    @Getter
    @Setter
    public static class ApiHeader implements KeyValueAware {

        @NotBlank(message = "header key is required")
        private String key;

        @NotBlank(message = "header value is required")
        private String value;

        private String description;

    }

    @Getter
    @Setter
    public static class ApiQueryParam implements KeyValueAware {

        @NotBlank(message = "query parameter key is required")
        private String key;

        private String exampleValue;

        @NotNull(message = "query parameter type is required")
        private QueryParamType type;

        @Size(min = 3, message = "query parameter description must be at least 3 characters")
        private String description;

        @Valid
        @NoDuplicateKeys(message = "Duplicate keys are not allowed inside query parameter children")
        private List<ApiQueryParam> children;

        @AssertTrue(message = "OBJECT query params must declare at least one child; other types cannot have children")
        public boolean isValidChildrenConfiguration() {
            if (type == null) {
                return true;
            }
            if (type == QueryParamType.OBJECT) {
                return children != null && !children.isEmpty();
            }
            return children == null || children.isEmpty();
        }

        @AssertTrue(message = "OBJECT query params cannot define exampleValue")
        public boolean isExampleValueValidForType() {
            if (type == null) {
                return true;
            }
            if (type == QueryParamType.OBJECT) {
                return exampleValue == null || exampleValue.isBlank();
            }
            return true;
        }
    }

    @Getter
    @Setter
    public static class ApiBodyParam implements KeyValueAware {

        @NotBlank(message = "body parameter key is required")
        private String key;

        @NotBlank(message = "body parameter exampleValue is required")
        private String exampleValue;

        @NotNull(message = "body parameter type is required")
        private BodyParamType type;

        @Size(min = 3, message = "body parameter description must be at least 3 characters")
        private String description;

    }
}
