package dev.squadx.repository;

import dev.squadx.model.AutopilotRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutopilotRunRepository extends JpaRepository<AutopilotRun, Long> {

    Page<AutopilotRun> findByAutopilotIdOrderByTriggeredAtDesc(Long autopilotId, Pageable pageable);
}
