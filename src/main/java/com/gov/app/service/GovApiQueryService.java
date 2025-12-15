package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.GovApiRegistration;
import com.gov.app.dto.GovApiListItemResponse;
import com.gov.app.dto.GovApiListResponse;
import com.gov.app.exception.ValidationException;
import com.gov.app.repository.GovApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GovApiQueryService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Map<String, String> SORT_FIELD_MAPPING = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "baseUrl", "baseUrl"
    );
    private static final String STATUS_REGISTERED = "REGISTERED";

    private final GovApiRegistrationRepository repository;
    private final ObjectMapper objectMapper;

    public GovApiListResponse search(UUID id,
                                     String description,
                                     int page,
                                     int size,
                                     String sort,
                                     Map<String, String> rawFilters) {
        if (id != null) {
            return searchById(id);
        }

        String normalizedDescription = normalizeDescription(description);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<GovApiRegistration> specification = buildSpecification(normalizedDescription, rawFilters);

        Page<GovApiRegistration> result = repository.findAll(specification, pageable);
        List<GovApiListItemResponse> items = result.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return GovApiListResponse.builder()
                .items(items)
                .page(result.getNumber())
                .size(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private GovApiListResponse searchById(UUID id) {
        List<GovApiListItemResponse> items = repository.findById(id)
                .map(this::toListItem)
                .map(List::of)
                .orElseGet(Collections::emptyList);

        long totalItems = items.size();
        long totalPages = totalItems == 0 ? 0 : 1;

        return GovApiListResponse.builder()
                .items(items)
                .page(0)
                .size(1)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    private Sort resolveSort(String sortParam) {
        if (!StringUtils.hasText(sortParam)) {
            return DEFAULT_SORT;
        }

        String[] parts = sortParam.split(",");
        if (parts.length != 2) {
            throw new ValidationException("sort must be provided as <field>,<asc|desc>");
        }

        String field = parts[0].trim();
        String direction = parts[1].trim();

        String mappedField = SORT_FIELD_MAPPING.get(field);
        if (mappedField == null) {
            throw new ValidationException("Unsupported sort field: " + field);
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("sort direction must be asc or desc");
        }

        return Sort.by(sortDirection, mappedField);
    }

    private Specification<GovApiRegistration> buildSpecification(String description, Map<String, String> rawFilters) {
        Specification<GovApiRegistration> specification = Specification.where(null);

        if (StringUtils.hasText(description)) {
            String lowered = description.toLowerCase(Locale.US);
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + lowered + "%"));
        }

        Map<String, String> filters = rawFilters != null ? rawFilters : Collections.emptyMap();

        String httpMethod = filters.get("httpMethod");
        if (StringUtils.hasText(httpMethod)) {
            specification = specification.and((root, query, cb) -> cb.equal(cb.upper(root.get("httpMethod")),
                    httpMethod.toUpperCase(Locale.US)));
        }

        return specification;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }

        String trimmed = description.trim();
        if (trimmed.length() < 3) {
            throw new ValidationException("description must be at least 3 characters");
        }
        return trimmed;
    }

    private GovApiListItemResponse toListItem(GovApiRegistration entity) {
        OffsetDateTime createdAt = entity.getCreatedAt() != null
                ? OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC)
                : null;

        return GovApiListItemResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .baseUrl(entity.getBaseUrl())
                .httpMethod(entity.getHttpMethod())
                .headers(parseHeaders(entity.getHeadersJson()))
                .queryParams(parseQueryParams(entity.getQueryParamsJson()))
                .bodyParams(parseBodyParams(entity.getBodyParamsJson()))
                .description(entity.getDescription())
                .status(STATUS_REGISTERED)
                .createdAt(createdAt)
                .build();
    }

    private List<GovApiListItemResponse.ApiHeaderResponse> parseHeaders(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<GovApiListItemResponse.ApiHeaderResponse> headers = new ArrayList<>();
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                headers.add(GovApiListItemResponse.ApiHeaderResponse.builder()
                        .key(entry.getKey())
                        .value(asText(value, "value"))
                        .description(asText(value, "description"))
                        .build());
            });
            return headers;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored headers payload is invalid", e);
        }
    }

    private List<GovApiListItemResponse.ApiQueryParamResponse> parseQueryParams(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<GovApiListItemResponse.ApiQueryParamResponse> params = new ArrayList<>();
            node.fields().forEachRemaining(entry -> params.add(toQueryParam(entry.getKey(), entry.getValue())));
            return params;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored query parameters payload is invalid", e);
        }
    }

    private GovApiListItemResponse.ApiQueryParamResponse toQueryParam(String key, JsonNode node) {
        List<GovApiListItemResponse.ApiQueryParamResponse> children = Collections.emptyList();
        JsonNode childrenNode = node.get("children");
        if (childrenNode != null && childrenNode.isObject()) {
            List<GovApiListItemResponse.ApiQueryParamResponse> computedChildren = new ArrayList<>();
            childrenNode.fields().forEachRemaining(entry -> computedChildren.add(toQueryParam(entry.getKey(), entry.getValue())));
            children = computedChildren;
        }

        return GovApiListItemResponse.ApiQueryParamResponse.builder()
                .key(key)
                .type(asText(node, "type"))
                .description(asText(node, "description"))
                .exampleValue(asText(node, "exampleValue"))
                .children(children)
                .build();
    }

    private List<GovApiListItemResponse.ApiBodyParamResponse> parseBodyParams(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<GovApiListItemResponse.ApiBodyParamResponse> bodyParams = new ArrayList<>();
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                bodyParams.add(GovApiListItemResponse.ApiBodyParamResponse.builder()
                        .key(entry.getKey())
                        .type(asText(value, "type"))
                        .exampleValue(asText(value, "exampleValue"))
                        .description(asText(value, "description"))
                        .build());
            });
            return bodyParams;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored body parameters payload is invalid", e);
        }
    }

    private String asText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
