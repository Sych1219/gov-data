package com.gov.app.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gov_openapi_endpoint")
public class GovOpenApiEndpoint {

    @Id
    private UUID id;

    @Column("openapi_id")
    private UUID openapiId;

    @Column("path")
    private String path;

    @Column("http_method")
    private String httpMethod;

    @Column("operation_id")
    private String operationId;

    @Column("summary")
    private String summary;

    @Column("description")
    private String description;

    @Column("parameters_json")
    private String parametersJson;

    @Column("request_body_json")
    private String requestBodyJson;

    @Column("responses_json")
    private String responsesJson;

    @Column("security_json")
    private String securityJson;

    @Column("tags")
    private List<String> tags;

    @Column("created_at")
    private Instant createdAt;
}
