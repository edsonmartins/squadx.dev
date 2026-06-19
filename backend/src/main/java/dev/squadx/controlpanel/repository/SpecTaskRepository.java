package dev.squadx.controlpanel.repository;

import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecTaskRepository extends JpaRepository<SpecTask, Long> {

    List<SpecTask> findByChangeId(Long changeId);

    /** Tarefas num dado estado — usado pelo poller para varrer PRs em validação. */
    List<SpecTask> findByStatus(SpecTaskStatus status);

    List<SpecTask> findByRequirementId(Long requirementId);

    @Query("SELECT t FROM SpecTask t WHERE t.change.project.id = :projectId")
    List<SpecTask> findByProjectId(Long projectId);

    /** Contagem por status para a projeção "onde estamos" (R7). */
    @Query("SELECT t.status, COUNT(t) FROM SpecTask t WHERE t.change.project.id = :projectId GROUP BY t.status")
    List<Object[]> countByStatusForProject(Long projectId);
}
