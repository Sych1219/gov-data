package com.gov.app.controller;

import com.gov.app.dto.request.StoreAnalysisRequest;
import com.gov.app.dto.response.ApiResponse;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraListAllResponse;
import com.gov.app.dto.response.CameraListResponse;
import com.gov.app.dto.response.NearbyResponse;
import com.gov.app.dto.response.SearchResponse;
import com.gov.app.dto.response.StoreAnalysisResponse;
import com.gov.app.service.CameraAnalysisService;
import com.gov.app.service.TrafficImageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Traffic Cameras", description = "Real-time traffic camera images and metadata")
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
@Validated
public class TrafficImageController {

    private final TrafficImageQueryService queryService;
    private final CameraAnalysisService analysisService;

    @Operation(summary = "List all cameras with latest snapshot")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping
    public ApiResponse<CameraListAllResponse> listAll() {
        log.info("GET /api/cameras");
        return ApiResponse.ok(queryService.listAllCameras());
    }

    @Operation(summary = "Single camera detail with latest image")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Camera not found")
    })
    @GetMapping("/{id}")
    public ApiResponse<CameraDetail> getById(
            @Parameter(description = "Camera ID, e.g. 2701", example = "2701")
            @PathVariable Long id) {
        log.info("GET /api/cameras/{}", id);
        return ApiResponse.ok(queryService.getCameraById(id));
    }

    @Operation(summary = "Cameras within radius of a point")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping("/nearby")
    public ApiResponse<NearbyResponse> nearby(
            @Parameter(description = "Latitude", example = "1.3521")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,

            @Parameter(description = "Longitude", example = "103.8198")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,

            @Parameter(description = "Radius in metres", example = "5000")
            @RequestParam(defaultValue = "5000") @Positive int radius) {
        log.info("GET /api/cameras/nearby - lat={}, lng={}, radius={}", lat, lng, radius);
        return ApiResponse.ok(queryService.getNearbyCameras(lat, lng, radius));
    }

    @Operation(summary = "All cameras along an expressway")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown expressway code")
    })
    @GetMapping("/expressway/{code}")
    public ApiResponse<CameraListResponse> byExpressway(
            @Parameter(description = "Expressway code, e.g. BKE, PIE, CTE", example = "BKE")
            @PathVariable String code) {
        log.info("GET /api/cameras/expressway/{}", code);
        return ApiResponse.ok(queryService.getCamerasByExpressway(code));
    }

    @Operation(summary = "Search cameras by location name")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    @GetMapping("/search")
    public ApiResponse<SearchResponse> search(
            @Parameter(description = "Search keyword, e.g. Woodlands, Tampines", example = "Woodlands")
            @RequestParam @NotBlank String q) {
        log.info("GET /api/cameras/search?q={}", q);
        return ApiResponse.ok(queryService.searchCameras(q));
    }

    @Operation(summary = "Store LLM vision analysis for a camera",
               description = "Upserts an analysis result produced by civic-app into camera_analysis. Requires an existing snapshot.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analysis stored"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid fields"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Camera not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "No snapshot exists yet for this camera")
    })
    @PostMapping("/{id}/analysis")
    public ApiResponse<StoreAnalysisResponse> storeAnalysis(
            @Parameter(description = "Camera ID, e.g. 1701", example = "1701")
            @PathVariable Long id,
            @Valid @RequestBody StoreAnalysisRequest request) {
        log.info("POST /api/cameras/{}/analysis", id);
        return ApiResponse.ok(analysisService.storeAnalysis(id, request));
    }
}
