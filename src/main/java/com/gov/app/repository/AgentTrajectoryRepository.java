package com.gov.app.repository;

import com.gov.app.domain.AgentTrajectory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTrajectoryRepository extends JpaRepository<AgentTrajectory, String> {}
