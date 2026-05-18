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
@Table(name = "agent_hints")
public class AgentHint {

    @Id
    private String id;

    private String agent;
    private String body;

    /** pending | active | retired */
    private String status;

    @Column(name = "seen_count")
    private int seenCount;

    @Column(name = "embedding_json", columnDefinition = "text")
    private String embeddingJson;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
