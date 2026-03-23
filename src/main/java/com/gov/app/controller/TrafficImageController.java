package com.gov.app.controller;

import com.gov.app.dto.response.ApiResponse;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraListAllResponse;
import com.gov.app.dto.response.CameraListResponse;
import com.gov.app.dto.response.NearbyResponse;
import com.gov.app.dto.response.SearchResponse;
import com.gov.app.service.TrafficImageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Traffic Cameras", description = "Real-time traffic camera images and metadata")
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
@Validated
public class TrafficImageController {

    private final TrafficImageQueryService queryService;

    @Operation(summary = "List all cameras with latest snapshot")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping
    public Mono<ApiResponse<CameraListAllResponse>> listAll() {
        log.info("GET /api/cameras");
        return queryService.listAllCameras().map(ApiResponse::ok);
    }

    @Operation(summary = "Single camera detail with latest image")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Camera not found")
    })
    @GetMapping("/{id}")
    public Mono<ApiResponse<CameraDetail>> getById(
            @Parameter(description = "Camera ID, e.g. 2701", example = "2701")
            @PathVariable Long id) {
        log.info("GET /api/cameras/{}", id);
        return queryService.getCameraById(id).map(ApiResponse::ok);
    }

    @Operation(summary = "Cameras within radius of a point")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping("/nearby")
    public Mono<ApiResponse<NearbyResponse>> nearby(
            @Parameter(description = "Latitude", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,

            @Parameter(description = "Radius in metres", example = "5000")
            @RequestParam(defaultValue = "5000") @Positive int radius) {
        log.info("GET /api/cameras/nearby - lat={}, lng={}, radius={}", lat, lng, radius);
        return queryService.getNearbyCameras(lat, lng, radius).map(ApiResponse::ok);
    }

    @Operation(summary = "All cameras along an expressway")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown expressway code")
    })
    @GetMapping("/expressway/{code}")
    public Mono<ApiResponse<CameraListResponse>> byExpressway(
            @Parameter(description = "Expressway code, e.g. BKE, PIE, CTE", example = "BKE")
            @PathVariable String code) {
        log.info("GET /api/cameras/expressway/{}", code);
        return queryService.getCamerasByExpressway(code).map(ApiResponse::ok);
    }

    @Operation(summary = "Search cameras by location name")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping("/search")
    public Mono<ApiResponse<SearchResponse>> search(
            @Parameter(description = "Search keyword, e.g. Woodlands, Tampines", example = "Woodlands")
            @RequestParam @NotBlank String q) {
        log.info("GET /api/cameras/search?q={}", q);
        return queryService.searchCameras(q).map(ApiResponse::ok);
    }
}
