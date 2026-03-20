package dev.squadx.repository;

import dev.squadx.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    List<TaskDependency> findByTaskId(Long taskId);

    List<TaskDependency> findByDependsOnId(Long dependsOnId);

    void deleteByTaskIdAndDependsOnId(Long taskId, Long dependsOnId);

    boolean existsByTaskIdAndDependsOnId(Long taskId, Long dependsOnId);
}
