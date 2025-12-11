package com.gov.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public String getQueryParamsJson() {
        return queryParamsJson;
    }

    public void setQueryParamsJson(String queryParamsJson) {
        this.queryParamsJson = queryParamsJson;
    }

    public String getBodyParamsJson() {
        return bodyParamsJson;
    }

    public void setBodyParamsJson(String bodyParamsJson) {
        this.bodyParamsJson = bodyParamsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
