package com.gov.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Entity
@Table(name = "gov_api_registration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gov_api_registration_name_url", columnNames = {"name", "base_url"})
})
public class GovApiRegistration {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "query_params_json", columnDefinition = "TEXT")
    private String queryParamsJson;

    @Column(name = "body_params_json", columnDefinition = "TEXT")
    private String bodyParamsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
