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
@Table("gov_openapi_registration")
public class GovOpenApiRegistration {

    @Id
    private UUID id;

    @Column("name")
    private String name;

    @Column("title")
    private String title;

    @Column("version")
    private String version;

    @Column("description")
    private String description;

    @Column("category")
    private String category;

    @Column("base_url")
    private String baseUrl;

    @Column("openapi_version")
    private String openapiVersion;

    @Column("openapi_spec_json")
    private String openapiSpecJson;

    @Column("endpoint_count")
    private Integer endpointCount;

    @Column("tags")
    private List<String> tags;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
