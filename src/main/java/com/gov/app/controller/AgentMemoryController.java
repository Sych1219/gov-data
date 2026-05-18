package com.gov.app.controller;

import com.gov.app.dto.request.CreateHintRequest;
import com.gov.app.dto.request.RecordObservationRequest;
import com.gov.app.dto.request.SaveTrajectoryRequest;
import com.gov.app.dto.request.UpdateHintRequest;
import com.gov.app.dto.response.AgentHintResponse;
import com.gov.app.dto.response.AgentObservationResponse;
import com.gov.app.dto.response.ApiResponse;
import com.gov.app.service.AgentMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Agent Memory", description = "Store and retrieve agent learning trajectories and hints")
@RestController
@RequestMapping("/api/v1/agent-memory")
@RequiredArgsConstructor
@Validated
public class AgentMemoryController {

    private final AgentMemoryService service;

    @Operation(summary = "Save a request trajectory")
    @PostMapping("/trajectories")
    public ApiResponse<Void> saveTrajectory(@Valid @RequestBody SaveTrajectoryRequest req) {
        log.info("POST /trajectories - agent={}, requestId={}, iterations={}",
            req.getAgent(), req.getId(), req.getIterations());
        service.saveTrajectory(req);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Get all hints for an agent")
    @GetMapping("/hints")
    public ApiResponse<List<AgentHintResponse>> getHints(@RequestParam @NotBlank String agent) {
        log.info("GET /hints - agent={}", agent);
        return ApiResponse.ok(service.getHints(agent));
    }

    @Operation(summary = "Create a new hint")
    @PostMapping("/hints")
    public ApiResponse<AgentHintResponse> createHint(@Valid @RequestBody CreateHintRequest req) {
        log.info("POST /hints - agent={}", req.getAgent());
        return ApiResponse.ok(service.createHint(req));
    }

    @Operation(summary = "Update hint status or seen_count")
    @PatchMapping("/hints/{id}")
    public ApiResponse<AgentHintResponse> updateHint(
            @PathVariable String id,
            @RequestBody UpdateHintRequest req) {
        log.info("PATCH /hints/{} - status={}, seenCount={}", id, req.getStatus(), req.getSeenCount());
        return ApiResponse.ok(service.updateHint(id, req));
    }

    @Operation(summary = "Record a hint observation for one run")
    @PostMapping("/hints/{id}/observations")
    public ApiResponse<Void> recordObservation(
            @PathVariable String id,
            @Valid @RequestBody RecordObservationRequest req) {
        log.info("POST /hints/{}/observations - requestId={}, present={}, iters={}",
            id, req.getRequestId(), req.isHintPresent(), req.getIterations());
        service.recordObservation(id, req);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Get all observations for a hint")
    @GetMapping("/hints/{id}/observations")
    public ApiResponse<List<AgentObservationResponse>> getObservations(@PathVariable String id) {
        log.info("GET /hints/{}/observations", id);
        return ApiResponse.ok(service.getObservations(id));
    }
}
