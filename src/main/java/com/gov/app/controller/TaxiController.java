package com.gov.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.dto.*;
import com.gov.app.service.TaxiQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/taxis")
@RequiredArgsConstructor
@Validated
public class TaxiController {

    private final TaxiQueryService queryService;
    private final ObjectMapper objectMapper;

    /** 4.1 Count taxis within radius */
    @GetMapping("/nearby/count")
    public Mono<TaxiNearbyCountResponse> nearbyCount(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,
            @RequestParam @Positive int radius,
            @RequestParam(required = false) String datetime) {
        return queryService.countNearby(lat, lon, radius, datetime);
    }

    /** 4.2 List taxis within radius (GeoJSON FeatureCollection) */
    @GetMapping("/nearby")
    public Mono<TaxiNearbyListResponse> nearbyList(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,
            @RequestParam @Positive int radius,
            @RequestParam(defaultValue = "100") @Positive int limit,
            @RequestParam(required = false) String datetime) {
        return queryService.listNearby(lat, lon, radius, limit, datetime);
    }

    /** 4.3 Nearest N taxis with distances */
    @GetMapping("/nearest")
    public Mono<TaxiNearestResponse> nearest(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon,
            @RequestParam(defaultValue = "5") @Positive int limit,
            @RequestParam(required = false) String datetime) {
        return queryService.findNearest(lat, lon, limit, datetime);
    }

    /** 4.4 Count taxis in a named zone */
    @GetMapping("/zone/{zoneName}/count")
    public Mono<TaxiZoneCountResponse> zoneCount(
            @PathVariable String zoneName,
            @RequestParam(required = false) String datetime) {
        return queryService.countInZone(zoneName, datetime);
    }

    /** 4.5 Count taxis in a custom GeoJSON polygon */
    @PostMapping("/polygon/count")
    public Mono<Map<String, Object>> polygonCount(@Valid @RequestBody TaxiPolygonRequest request) {
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
    @GetMapping("/road/{roadName}/count")
    public Mono<Map<String, Object>> roadCount(
            @PathVariable String roadName,
            @RequestParam(defaultValue = "100") @Positive int buffer_m,
            @RequestParam(required = false) String datetime) {
        return queryService.countNearRoad(roadName, buffer_m, datetime)
                .map(count -> Map.of("road", roadName, "taxi_count", count));
    }

    /** 4.7 Count taxis along a custom route buffer */
    @PostMapping("/route/count")
    public Mono<Map<String, Object>> routeCount(@Valid @RequestBody TaxiRouteRequest request) {
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
    @GetMapping("/history/snapshots")
    public Mono<TaxiHistoryResponse> historySnapshots(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String zone) {
        return queryService.getHistory(start, end, zone);
    }

    /** 4.9 Recent activity delta (taxi count change in last N minutes) */
    @GetMapping("/history/recent")
    public Mono<Map<String, Object>> recentActivity(
            @RequestParam(defaultValue = "15") int minutes) {
        return queryService.getRecentDelta(minutes)
                .map(delta -> Map.of("delta", delta, "minutes", minutes));
    }
}
