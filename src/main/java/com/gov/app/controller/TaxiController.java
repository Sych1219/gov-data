package com.gov.app.controller;

import com.gov.app.dto.request.TaxiPolygonRequest;
import com.gov.app.dto.request.TaxiRouteRequest;
import com.gov.app.dto.response.ApiResponse;
import com.gov.app.dto.response.TaxiResponseData;
import com.gov.app.service.TaxiQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Taxi Availability", description = "Real-time and historical taxi position queries")
@RestController
@RequestMapping("/api/v1/taxis")
@RequiredArgsConstructor
@Validated
public class TaxiController {

    private final TaxiQueryService queryService;

    /** 4.1 Taxis within radius (count + GeoJSON locations) */
    @Operation(summary = "Taxis within radius",
               description = "Returns the count and GeoJSON locations of taxis within `radius` metres of the given coordinate.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/nearby")
    public Mono<ApiResponse<TaxiResponseData>> nearby(
            @Parameter(description = "Latitude (-90 to 90)", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude (-180 to 180)", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,

            @Parameter(description = "Search radius in metres", example = "500")
            @RequestParam @Positive int radius,

            @Parameter(description = "Max locations returned (default 100)", example = "50")
            @RequestParam(defaultValue = "100") @Positive int limit,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        log.info("GET /nearby - lat={}, lon={}, radius={}, limit={}, datetime={}", lat, lon, radius, limit, datetime);
        return queryService.nearby(lat, lon, radius, limit, datetime).map(ApiResponse::ok);
    }

    /** 4.2 Nearest N taxis with distances */
    @Operation(summary = "Nearest N taxis with distances",
               description = "Returns the N closest available taxis and their distances from the given coordinate.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/nearest")
    public Mono<ApiResponse<TaxiResponseData>> nearest(
            @Parameter(description = "Latitude (-90 to 90)", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude (-180 to 180)", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,

            @Parameter(description = "Number of taxis to return", example = "5")
            @RequestParam(defaultValue = "5") @Positive int limit,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        log.info("GET /nearest - lat={}, lon={}, limit={}, datetime={}", lat, lon, limit, datetime);
        return queryService.findNearest(lat, lon, limit, datetime).map(ApiResponse::ok);
    }

    /** 4.3 Count taxis in a named zone */
    @Operation(summary = "Count taxis in a named zone",
               description = "Returns the number of available taxis within the boundaries of the named zone.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Zone not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/zone/{zoneName}/count")
    public Mono<ApiResponse<TaxiResponseData>> zoneCount(
            @Parameter(description = "Zone identifier, e.g. 'CBD', 'Changi'", example = "CBD")
            @PathVariable String zoneName,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        log.info("GET /zone/{}/count - datetime={}", zoneName, datetime);
        return queryService.countInZone(zoneName, datetime).map(ApiResponse::ok);
    }

    /** 4.4 Count taxis in a custom GeoJSON polygon */
    @Operation(summary = "Count taxis in a custom GeoJSON polygon",
               description = "POST a GeoJSON Polygon geometry. Returns the taxi count inside it.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid polygon GeoJSON",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PostMapping(value = "/polygon/count", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<TaxiResponseData>> polygonCount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "GeoJSON Polygon geometry and optional target datetime",
                required = true)
            @Valid @RequestBody TaxiPolygonRequest request) {
        log.info("POST /polygon/count - datetime={}", request.getDatetime());
        return queryService.countInPolygon(request.getPolygon(), request.getDatetime()).map(ApiResponse::ok);
    }

    /** 4.5 Count taxis near a named road/highway */
    @Operation(summary = "Count taxis near a named road",
               description = "Counts taxis within `buffer_m` metres of the named road or highway.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/road/{roadName}/count")
    public Mono<ApiResponse<TaxiResponseData>> roadCount(
            @Parameter(description = "Road or highway name, e.g. 'PIE', 'Orchard Road'", example = "PIE")
            @PathVariable String roadName,

            @Parameter(description = "Buffer distance in metres", example = "100")
            @RequestParam(defaultValue = "100") @Positive int buffer_m,

            @Parameter(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
            @RequestParam(required = false) String datetime) {
        log.info("GET /road/{}/count - buffer_m={}, datetime={}", roadName, buffer_m, datetime);
        return queryService.countNearRoad(roadName, buffer_m, datetime).map(ApiResponse::ok);
    }

    /** 4.6 Count taxis along a custom route buffer */
    @Operation(summary = "Count taxis along a custom route",
               description = "POST a GeoJSON LineString route. Counts taxis within `buffer_m` metres of the route.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid route GeoJSON",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PostMapping(value = "/route/count", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<TaxiResponseData>> routeCount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "GeoJSON LineString route, buffer distance, and optional target datetime",
                required = true)
            @Valid @RequestBody TaxiRouteRequest request) {
        log.info("POST /route/count - buffer_m={}, datetime={}", request.getBufferM(), request.getDatetime());
        return queryService.countAlongRoute(request.getRoute(), request.getBufferM(), request.getDatetime()).map(ApiResponse::ok);
    }

    /** 4.7 Historical snapshots in a time range */
    @Operation(summary = "Historical snapshots in a time range",
               description = "Returns per-snapshot taxi positions between `start` and `end`, optionally filtered by zone.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid time range or range exceeds 7 days",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Zone not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/history/snapshots")
    public Mono<ApiResponse<TaxiResponseData>> historySnapshots(
            @Parameter(description = "Start of range, ISO-8601 SGT", example = "2026-03-09T00:00:00+08:00", required = true)
            @RequestParam String start,

            @Parameter(description = "End of range, ISO-8601 SGT", example = "2026-03-09T08:00:00+08:00", required = true)
            @RequestParam String end,

            @Parameter(description = "Optional zone filter, e.g. 'CBD'")
            @RequestParam(required = false) String zone) {
        log.info("GET /history/snapshots - start={}, end={}, zone={}", start, end, zone);
        return queryService.getHistory(start, end, zone).map(ApiResponse::ok);
    }

    /** 4.8 Recent activity timeline */
    @Operation(summary = "Recent activity timeline",
               description = "Returns per-snapshot taxi positions across the last N minutes.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/history/recent")
    public Mono<ApiResponse<TaxiResponseData>> recentActivity(
            @Parameter(description = "Look-back window in minutes", example = "15")
            @RequestParam(defaultValue = "15") int minutes) {
        log.info("GET /history/recent - minutes={}", minutes);
        return queryService.getRecentTimeline(minutes).map(ApiResponse::ok);
    }
}
