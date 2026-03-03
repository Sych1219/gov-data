package com.gov.app.controller;

import com.gov.app.dto.ZoneListResponse;
import com.gov.app.repository.ZoneRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Zones", description = "Available zone catalog for zone-based taxi queries")
@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;

    @Operation(
            summary = "List available zones",
            description = "Returns all zone names and categories. "
                    + "Filter by `?category=district`, `?category=road`, or `?category=highway`."
    )
    @GetMapping
    public Mono<ZoneListResponse> listZones(
            @Parameter(description = "Optional category filter: district, road, or highway")
            @RequestParam(required = false) String category) {

        var zones = category != null
                ? zoneRepository.findByCategory(category)
                : zoneRepository.findAll();

        return zones
                .map(z -> new ZoneListResponse.ZoneEntry(z.getName(), z.getCategory()))
                .collectList()
                .map(ZoneListResponse::new);
    }
}
