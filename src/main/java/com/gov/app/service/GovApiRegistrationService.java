package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.GovApiRegistration;
import com.gov.app.dto.GovApiRegistrationRequest;
import com.gov.app.dto.GovApiRegistrationResponse;
import com.gov.app.dto.QueryParamType;
import com.gov.app.exception.ConflictException;
import com.gov.app.repository.GovApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GovApiRegistrationService {

    private final GovApiRegistrationRepository repository;
    private final ObjectMapper objectMapper;

    public GovApiRegistrationResponse register(GovApiRegistrationRequest request) {
        if (repository.existsByNameIgnoreCaseAndBaseUrl(request.getName(), request.getBaseUrl())) {
            throw new ConflictException("Registration already exists for the provided name and baseUrl");
        }

        GovApiRegistration registration = new GovApiRegistration();
        registration.setId(UUID.randomUUID());
        registration.setName(request.getName());
        registration.setDescription(request.getDescription());
        registration.setBaseUrl(request.getBaseUrl());
        registration.setHttpMethod(request.getHttpMethod().name());
        registration.setHeadersJson(toJson(normalizeHeaders(request)));
        registration.setQueryParamsJson(toJson(normalizeQueryParams(request)));
        registration.setBodyParamsJson(toJson(normalizeBodyParams(request)));
        registration.setCreatedAt(Instant.now());

        GovApiRegistration saved = repository.save(registration);
        return new GovApiRegistrationResponse(
                saved.getId(),
                saved.getName(),
                "REGISTERED",
                OffsetDateTime.ofInstant(saved.getCreatedAt(), ZoneOffset.UTC)
        );
    }

    private Map<String, Object> normalizeHeaders(GovApiRegistrationRequest request) {
        if (request.getHeaders() == null || request.getHeaders().isEmpty()) {
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (GovApiRegistrationRequest.ApiHeader header : request.getHeaders()) {
            if (header == null) {
                continue;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", header.getValue());
            if (header.getDescription() != null) {
                value.put("description", header.getDescription());
            }
            normalized.put(header.getKey(), value);
        }
        return normalized;
    }

    private Map<String, Object> normalizeQueryParams(GovApiRegistrationRequest request) {
        if (request.getQueryParams() == null || request.getQueryParams().isEmpty()) {
            return null;
        }
        return toQueryParamNode(request.getQueryParams());
    }

    private Map<String, Object> toQueryParamNode(Iterable<GovApiRegistrationRequest.ApiQueryParam> params) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (GovApiRegistrationRequest.ApiQueryParam param : params) {
            if (param == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", param.getType().name());
            if (param.getDescription() != null) {
                node.put("description", param.getDescription());
            }
            if (param.getType() == QueryParamType.OBJECT) {
                if (param.getChildren() != null) {
                    node.put("children", toQueryParamNode(param.getChildren()));
                }
            } else {
                node.put("value", param.getValue());
            }
            normalized.put(param.getKey(), node);
        }
        return normalized;
    }

    private Map<String, Object> normalizeBodyParams(GovApiRegistrationRequest request) {
        if (request.getBodyParams() == null || request.getBodyParams().isEmpty()) {
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (GovApiRegistrationRequest.ApiBodyParam param : request.getBodyParams()) {
            if (param == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", param.getType().name());
            node.put("value", param.getValue());
            if (param.getDescription() != null) {
                node.put("description", param.getDescription());
            }
            normalized.put(param.getKey(), node);
        }
        return normalized;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize registration payload", e);
        }
    }
}
