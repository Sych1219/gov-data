package com.gov.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_trajectories")
public class AgentTrajectory {

    @Id
    private String id;

    private String agent;
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events_json", columnDefinition = "jsonb")
    private String eventsJson;

    private int iterations;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
