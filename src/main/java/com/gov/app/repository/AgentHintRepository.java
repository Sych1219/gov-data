package com.gov.app.repository;

import com.gov.app.domain.AgentHint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentHintRepository extends JpaRepository<AgentHint, String> {

    List<AgentHint> findByAgentOrderBySeenCountDesc(String agent);

    List<AgentHint> findByAgentAndStatusInOrderBySeenCountDesc(String agent, List<String> statuses);

    List<AgentHint> findByAgentAndStatusOrderBySeenCountDesc(String agent, String status);
}
