package com.gov.app.repository;

import com.gov.app.domain.GovApiRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GovApiRegistrationRepository extends JpaRepository<GovApiRegistration, UUID> {

    boolean existsByNameIgnoreCaseAndBaseUrl(String name, String baseUrl);
}
