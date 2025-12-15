package com.gov.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class GovApiListResponse {

    @Builder.Default
    private final List<GovApiListItemResponse> items = Collections.emptyList();

    private final int page;
    private final int size;
    private final long totalItems;
    private final long totalPages;
}
