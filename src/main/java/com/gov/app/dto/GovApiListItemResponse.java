package com.gov.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class GovApiListItemResponse {

    private final UUID id;
    private final String name;
    private final String baseUrl;
    private final String httpMethod;

    @Builder.Default
    private final List<ApiHeaderResponse> headers = Collections.emptyList();

    @Builder.Default
    private final List<ApiQueryParamResponse> queryParams = Collections.emptyList();

    @Builder.Default
    private final List<ApiBodyParamResponse> bodyParams = Collections.emptyList();

    private final String description;
    private final String status;
    private final OffsetDateTime createdAt;

    @Getter
    @Builder
    public static class ApiHeaderResponse {
        private final String key;
        private final String value;
        private final String description;
    }

    @Getter
    @Builder
    public static class ApiQueryParamResponse {
        private final String key;
        private final String type;
        private final String description;
        private final String exampleValue;

        @Builder.Default
        private final List<ApiQueryParamResponse> children = Collections.emptyList();
    }

    @Getter
    @Builder
    public static class ApiBodyParamResponse {
        private final String key;
        private final String type;
        private final String exampleValue;
        private final String description;
    }
}
