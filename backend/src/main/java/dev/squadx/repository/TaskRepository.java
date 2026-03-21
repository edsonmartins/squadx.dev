package dev.squadx.repository;

import dev.squadx.model.Task;
import dev.squadx.model.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    Page<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status, Pageable pageable);

    List<Task> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.parentTask IS NULL ORDER BY t.orderIndex")
    List<Task> findRootTasksByProjectId(Long projectId);

    @Query("SELECT t FROM Task t WHERE t.project.organization.id = :organizationId")
    Page<Task> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId")
    long countByProjectId(Long projectId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId AND t.status = :status")
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Modifying
    @Query("UPDATE Task t SET t.orderIndex = :orderIndex WHERE t.id = :taskId")
    void updateOrderIndex(Long taskId, Integer orderIndex);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.parentTask.id = :parentTaskId")
    long countSubtasksByParentTaskId(Long parentTaskId);

    List<Task> findByAssignedAgentId(Long agentId);
}
