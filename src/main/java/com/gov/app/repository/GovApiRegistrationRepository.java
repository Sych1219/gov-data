package com.gov.app.repository;

import com.gov.app.domain.GovApiRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface GovApiRegistrationRepository extends JpaRepository<GovApiRegistration, UUID>, JpaSpecificationExecutor<GovApiRegistration> {

    boolean existsByNameIgnoreCaseAndBaseUrl(String name, String baseUrl);
}
