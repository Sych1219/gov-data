package com.gov.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_hint_observations")
public class AgentHintObservation {

    @Id
    private String id;

    @Column(name = "hint_id")
    private String hintId;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "hint_present")
    private boolean hintPresent;

    private int iterations;
    private boolean success;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
