package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByRequirementId(Long requirementId);
}

