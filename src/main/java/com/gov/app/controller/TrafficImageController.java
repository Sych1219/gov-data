package com.gov.app.controller;

import com.gov.app.dto.request.StoreAnalysisRequest;
import com.gov.app.dto.response.ApiResponse;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraQueryResponse;
import com.gov.app.dto.response.StoreAnalysisResponse;
import com.gov.app.service.CameraAnalysisService;
import com.gov.app.service.TrafficImageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "Query cameras with optional filters",
               description = "Returns cameras filtered by expressway code, location keyword, or proximity. Returns all cameras when no filter is provided.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown expressway code")
    })
    @GetMapping
    public ApiResponse<CameraQueryResponse> listCameras(
            @Parameter(description = "Expressway code, e.g. BKE, PIE, CTE", example = "BKE")
            @RequestParam(required = false) String expressway,

            @Parameter(description = "Location keyword, e.g. Woodlands, Tampines", example = "Woodlands")
            @RequestParam(required = false) String search,

            @Parameter(description = "Latitude for proximity search", example = "1.3521")
            @RequestParam(required = false) Double lat,

            @Parameter(description = "Longitude for proximity search", example = "103.8198")
            @RequestParam(required = false) Double lng,

            @Parameter(description = "Radius in metres for proximity search", example = "5000")
            @RequestParam(required = false, defaultValue = "5000") @Positive int radius) {

        if (expressway != null) {
            log.info("GET /api/cameras?expressway={}", expressway);
            return ApiResponse.ok(queryService.getCamerasByExpressway(expressway));
        }
        if (search != null) {
            log.info("GET /api/cameras?search={}", search);
            return ApiResponse.ok(queryService.searchCameras(search));
        }
        if (lat != null && lng != null) {
            log.info("GET /api/cameras?lat={}&lng={}&radius={}", lat, lng, radius);
            return ApiResponse.ok(queryService.getNearbyCameras(lat, lng, radius));
        }
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
