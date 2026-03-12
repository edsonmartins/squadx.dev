package dev.squadx.repository;

import dev.squadx.model.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    List<ExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);
}
