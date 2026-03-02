package com.gov.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.dto.*;
import com.gov.app.service.TaxiQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Tag(name = "Taxi Availability", description = "Real-time and historical taxi position queries")
@RestController
@RequestMapping("/api/v1/taxis")
@RequiredArgsConstructor
@Validated
public class TaxiController {

    private final TaxiQueryService queryService;
    private final ObjectMapper objectMapper;

    /** 4.1 Count taxis within radius */
    @Operation(summary = "Count taxis within radius",
               description = "Returns the number of available taxis within `radius` metres of the given coordinate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/nearby/count")
    public Mono<TaxiNearbyCountResponse> nearbyCount(
            @Parameter(description = "Latitude (-90 to 90)", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude (-180 to 180)", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,

            @Parameter(description = "Search radius in metres", example = "500")
            @RequestParam @Positive int radius,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        return queryService.countNearby(lat, lon, radius, datetime);
    }

    /** 4.2 List taxis within radius (GeoJSON FeatureCollection) */
    @Operation(summary = "List taxis within radius",
               description = "Returns a GeoJSON FeatureCollection of taxis within `radius` metres.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/nearby")
    public Mono<TaxiNearbyListResponse> nearbyList(
            @Parameter(description = "Latitude (-90 to 90)", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude (-180 to 180)", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,

            @Parameter(description = "Search radius in metres", example = "500")
            @RequestParam @Positive int radius,

            @Parameter(description = "Max results to return (default 100)", example = "50")
            @RequestParam(defaultValue = "100") @Positive int limit,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        return queryService.listNearby(lat, lon, radius, limit, datetime);
    }

    /** 4.3 Nearest N taxis with distances */
    @Operation(summary = "Nearest N taxis with distances",
               description = "Returns the N closest available taxis and their distances from the given coordinate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/nearest")
    public Mono<TaxiNearestResponse> nearest(
            @Parameter(description = "Latitude (-90 to 90)", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude (-180 to 180)", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,

            @Parameter(description = "Number of taxis to return", example = "5")
            @RequestParam(defaultValue = "5") @Positive int limit,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        return queryService.findNearest(lat, lon, limit, datetime);
    }

    /** 4.4 Count taxis in a named zone */
    @Operation(summary = "Count taxis in a named zone",
               description = "Returns the number of available taxis within the boundaries of the named zone.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Zone not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/zone/{zoneName}/count")
    public Mono<TaxiZoneCountResponse> zoneCount(
            @Parameter(description = "Zone identifier, e.g. 'CBD', 'Changi'", example = "CBD")
            @PathVariable String zoneName,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        return queryService.countInZone(zoneName, datetime);
    }

    /** 4.5 Count taxis in a custom GeoJSON polygon */
    @Operation(summary = "Count taxis in a custom GeoJSON polygon",
               description = "POST a GeoJSON Polygon geometry. Returns the taxi count inside it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid polygon GeoJSON",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PostMapping(value = "/polygon/count", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> polygonCount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "GeoJSON Polygon geometry and optional target datetime",
                required = true)
            @Valid @RequestBody TaxiPolygonRequest request) {
        String polygonJson;
        try {
            polygonJson = objectMapper.writeValueAsString(request.getPolygon());
        } catch (Exception e) {
            return Mono.error(new com.gov.app.exception.ValidationException("Invalid polygon GeoJSON"));
        }
        return queryService.countInPolygon(polygonJson, request.getDatetime())
                .map(count -> Map.of("taxi_count", count));
    }

    /** 4.6 Count taxis near a named road/highway */
    @Operation(summary = "Count taxis near a named road",
               description = "Counts taxis within `buffer_m` metres of the named road or highway.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/road/{roadName}/count")
    public Mono<Map<String, Object>> roadCount(
            @Parameter(description = "Road or highway name, e.g. 'PIE', 'Orchard Road'", example = "PIE")
            @PathVariable String roadName,

            @Parameter(description = "Buffer distance in metres", example = "100")
            @RequestParam(defaultValue = "100") @Positive int buffer_m,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        return queryService.countNearRoad(roadName, buffer_m, datetime)
                .map(count -> Map.of("road", roadName, "taxi_count", count));
    }

    /** 4.7 Count taxis along a custom route buffer */
    @Operation(summary = "Count taxis along a custom route",
               description = "POST a GeoJSON LineString route. Counts taxis within `buffer_m` metres of the route.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid route GeoJSON",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PostMapping(value = "/route/count", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> routeCount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "GeoJSON LineString route, buffer distance, and optional target datetime",
                required = true)
            @Valid @RequestBody TaxiRouteRequest request) {
        String routeJson;
        try {
            routeJson = objectMapper.writeValueAsString(request.getRoute());
        } catch (Exception e) {
            return Mono.error(new com.gov.app.exception.ValidationException("Invalid route GeoJSON"));
        }
        return queryService.countAlongRoute(routeJson, request.getBufferM(), request.getDatetime())
                .map(count -> Map.of("taxi_count", count, "buffer_m", request.getBufferM()));
    }

    /** 4.8 Historical snapshots in a time range */
    @Operation(summary = "Historical snapshots in a time range",
               description = "Returns aggregated taxi count snapshots between `start` and `end`, optionally filtered by zone.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid time range",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/history/snapshots")
    public Mono<TaxiHistoryResponse> historySnapshots(
            @Parameter(description = "Start of range, ISO-8601 SGT", example = "2025-01-15T00:00:00+08:00", required = true)
            @RequestParam String start,

            @Parameter(description = "End of range, ISO-8601 SGT", example = "2025-01-15T08:00:00+08:00", required = true)
            @RequestParam String end,

            @Parameter(description = "Optional zone filter, e.g. 'CBD'")
            @RequestParam(required = false) String zone) {
        return queryService.getHistory(start, end, zone);
    }

    /** 4.9 Recent activity delta (taxi count change in last N minutes) */
    @Operation(summary = "Recent taxi count delta",
               description = "Returns the change in available taxi count over the last N minutes.")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/history/recent")
    public Mono<Map<String, Object>> recentActivity(
            @Parameter(description = "Look-back window in minutes", example = "15")
            @RequestParam(defaultValue = "15") int minutes) {
        return queryService.getRecentDelta(minutes)
                .map(delta -> Map.of("delta", delta, "minutes", minutes));
    }
}
