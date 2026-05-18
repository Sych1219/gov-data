package com.gov.app.service;

import com.gov.app.domain.AgentHint;
import com.gov.app.domain.AgentHintObservation;
import com.gov.app.domain.AgentTrajectory;
import com.gov.app.dto.request.CreateHintRequest;
import com.gov.app.dto.request.RecordObservationRequest;
import com.gov.app.dto.request.SaveTrajectoryRequest;
import com.gov.app.dto.request.UpdateHintRequest;
import com.gov.app.dto.response.AgentHintResponse;
import com.gov.app.dto.response.AgentObservationResponse;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.AgentHintObservationRepository;
import com.gov.app.repository.AgentHintRepository;
import com.gov.app.repository.AgentTrajectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AgentMemoryService {

    private final AgentTrajectoryRepository      trajectoryRepo;
    private final AgentHintRepository            hintRepo;
    private final AgentHintObservationRepository obsRepo;

    // ── Trajectories ──────────────────────────────────────────────────────────

    public void saveTrajectory(SaveTrajectoryRequest req) {
        trajectoryRepo.save(AgentTrajectory.builder()
            .id(req.getId())
            .agent(req.getAgent())
            .question(req.getQuestion())
            .eventsJson(req.getEventsJson())
            .iterations(req.getIterations())
            .createdAt(OffsetDateTime.now())
            .build());
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    public AgentHintResponse createHint(CreateHintRequest req) {
        AgentHint hint = AgentHint.builder()
            .id(UUID.randomUUID().toString())
            .agent(req.getAgent())
            .body(req.getBody())
            .status("pending")
            .seenCount(1)
            .embeddingJson(req.getEmbeddingJson())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        return toResponse(hintRepo.save(hint));
    }

    public AgentHintResponse updateHint(String id, UpdateHintRequest req) {
        AgentHint hint = hintRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Hint not found: " + id));
        if (req.getStatus()    != null) hint.setStatus(req.getStatus());
        if (req.getSeenCount() != null) hint.setSeenCount(req.getSeenCount());
        hint.setUpdatedAt(OffsetDateTime.now());
        return toResponse(hintRepo.save(hint));
    }

    @Transactional(readOnly = true)
    public List<AgentHintResponse> getHints(String agent) {
        return hintRepo.findByAgentOrderBySeenCountDesc(agent)
            .stream().map(this::toResponse).toList();
    }

    // ── Observations ──────────────────────────────────────────────────────────

    public void recordObservation(String hintId, RecordObservationRequest req) {
        hintRepo.findById(hintId)
            .orElseThrow(() -> new NotFoundException("Hint not found: " + hintId));
        obsRepo.save(AgentHintObservation.builder()
            .id(UUID.randomUUID().toString())
            .hintId(hintId)
            .requestId(req.getRequestId())
            .hintPresent(req.isHintPresent())
            .iterations(req.getIterations())
            .success(req.isSuccess())
            .createdAt(OffsetDateTime.now())
            .build());
    }

    @Transactional(readOnly = true)
    public List<AgentObservationResponse> getObservations(String hintId) {
        return obsRepo.findByHintId(hintId)
            .stream().map(this::toObsResponse).toList();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AgentHintResponse toResponse(AgentHint h) {
        return AgentHintResponse.builder()
            .id(h.getId()).agent(h.getAgent()).body(h.getBody())
            .status(h.getStatus()).seenCount(h.getSeenCount())
            .embeddingJson(h.getEmbeddingJson())
            .createdAt(h.getCreatedAt()).updatedAt(h.getUpdatedAt())
            .build();
    }

    private AgentObservationResponse toObsResponse(AgentHintObservation o) {
        return AgentObservationResponse.builder()
            .id(o.getId()).hintId(o.getHintId()).requestId(o.getRequestId())
            .hintPresent(o.isHintPresent()).iterations(o.getIterations())
            .success(o.isSuccess()).createdAt(o.getCreatedAt())
            .build();
    }
}
