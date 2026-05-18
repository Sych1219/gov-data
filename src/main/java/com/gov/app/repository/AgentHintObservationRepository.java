package com.gov.app.repository;

import com.gov.app.domain.AgentHintObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentHintObservationRepository extends JpaRepository<AgentHintObservation, String> {

    List<AgentHintObservation> findByHintId(String hintId);
}
