package com.gov.app.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("gov_api_registration")
public class GovApiRegistration {

    @Id
    @Column("id")
    private UUID id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("base_url")
    private String baseUrl;

    @Column("http_method")
    private String httpMethod;

    @Column("headers_json")
    private String headersJson;

    @Column("query_params_json")
    private String queryParamsJson;

    @Column("body_params_json")
    private String bodyParamsJson;

    @Column("created_at")
    private Instant createdAt;
}
