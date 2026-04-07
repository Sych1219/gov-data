package com.gov.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.Zone;
import com.gov.app.dto.ZoneListResponse;
import com.gov.app.dto.ZoneResolveResponse;
import com.gov.app.dto.response.ApiResponse;
import com.gov.app.dto.response.ZoneGeometryData;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.ZoneRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "Zones", description = "Available zone catalog for zone-based taxi queries")
@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "List available zones",
            description = "Returns all zone names and categories. "
                    + "Filter by `?category=district`, `?category=road`, or `?category=highway`."
    )
    @GetMapping
    public ApiResponse<ZoneListResponse> listZones(
            @Parameter(description = "Optional category filter: district, road, or highway")
            @RequestParam(required = false) String category) {
        List<Zone> zones = category != null
                ? zoneRepository.findByCategory(category)
                : zoneRepository.findAll();
        List<ZoneListResponse.ZoneEntry> entries = zones.stream()
                .map(z -> new ZoneListResponse.ZoneEntry(z.getName(), z.getCategory()))
                .toList();
        return ApiResponse.ok(new ZoneListResponse(entries));
    }

    @Operation(
            summary = "Resolve a place name to its category and suggested endpoint",
            description = "Fuzzy-matches a name across all categories (district, road, highway) and returns " +
                    "the canonical name, its category, and the recommended API endpoint to call."
    )
    @GetMapping("/resolve")
    public ApiResponse<ZoneResolveResponse> resolve(
            @Parameter(description = "Place name to resolve, e.g. 'AYE', 'CBD', 'Orchard Road'", example = "AYE")
            @RequestParam String name) {
        Zone zone = zoneRepository.findBestAnyMatch(name, 0.3)
                .orElseThrow(() -> new NotFoundException(
                        "No match found for '" + name + "'. Check GET /api/v1/zones for all available names."));
        return ApiResponse.ok(ZoneResolveResponse.builder()
                .name(zone.getName())
                .category(zone.getCategory())
                .suggestedEndpoint(suggestedEndpoint(zone))
                .build());
    }

    @Operation(
            summary = "Get geometry of a zone",
            description = "Returns the boundary or path of a zone. " +
                    "Districts return a Polygon; roads and highways return a LineString. " +
                    "This data is static — clients should cache it aggressively."
    )
    @GetMapping("/{name}/geometry")
    public ResponseEntity<ApiResponse<ZoneGeometryData>> getGeometry(
            @Parameter(description = "Zone name, e.g. 'tampines', 'aye', 'orchard-road'", example = "tampines")
            @PathVariable String name) {
        Zone zone = zoneRepository.findBestAnyMatch(name, 0.3)
                .orElseThrow(() -> new NotFoundException(
                        "No zone found matching '" + name + "'. Check GET /api/v1/zones for all available names."));
        ZoneRepository.ZoneGeometryJson raw = zoneRepository.findGeometryByName(zone.getName())
                .orElseThrow(() -> new NotFoundException("Geometry not found for zone: " + zone.getName()));
        try {
            var geometry = objectMapper.readTree(raw.geometryJson());
            var data = ZoneGeometryData.builder()
                    .name(raw.name())
                    .category(raw.category())
                    .geometry(geometry)
                    .build();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                    .body(ApiResponse.ok(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse zone geometry", e);
        }
    }

    private String suggestedEndpoint(Zone zone) {
        return switch (zone.getCategory()) {
            case "district" -> "GET /api/v1/taxis/zone/count?zoneName=" + zone.getName();
            case "road", "highway" -> "GET /api/v1/taxis/road/count?roadName=" + zone.getName();
            default -> "GET /api/v1/zones";
        };
    }
}
